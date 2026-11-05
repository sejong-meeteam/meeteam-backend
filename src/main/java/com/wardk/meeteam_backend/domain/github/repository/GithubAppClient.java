package com.wardk.meeteam_backend.domain.github.repository;

import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Metadata-only GitHub transport. Credentials are method-local and errors never retain upstream bodies. */
@Component
public class GithubAppClient {
 private final GithubAppProperties properties;
 private final ObjectMapper mapper;
 private final HttpClient http;
 @Autowired public GithubAppClient(GithubAppProperties p,ObjectMapper m) {this(p,m,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());}
 public GithubAppClient(GithubAppProperties p,ObjectMapper m,HttpClient h){properties=p;mapper=m;http=h;}
 public record Reply(int status,JsonNode body) {}
 public Reply request(long installationId,String method,String path,JsonNode body){
  if(installationId<=0)throw invalid();
  safePath(path); return authorizedRequest(installationId,method,path,body);
 }
 private Reply authorizedRequest(long installationId,String method,String path,JsonNode body){
  JsonNode token=send("POST","/app/installations/"+installationId+"/access_tokens",null,appJwt()).body();
  String value=token.path("token").asText(); if(!value.matches("[A-Za-z0-9_]+"))throw upstream();
  return send(method,path,body,value);
 }
 public JsonNode getInstallation(long installationId){if(installationId<=0)throw invalid();return send("GET","/app/installations/"+installationId,null,appJwt()).body();}
 public JsonNode graphQL(long installationId,String query,Map<String,Object> variables){
  if(query==null || query.isBlank())throw invalid();
  var body=mapper.createObjectNode();body.put("query",query);body.set("variables",mapper.valueToTree(variables));
  JsonNode result=request(installationId,"POST","/graphql",body).body();if(result.has("errors")||!result.has("data"))throw upstream();return result.path("data");
 }
 public List<JsonNode> paginate(long installationId,String path,String arrayField){
  safePath(path);List<JsonNode> all=new ArrayList<>();
  for(int page=1;page<=1000;page++){
   JsonNode root=authorizedRequest(installationId,"GET",pagePath(path,page),null).body();
   JsonNode rows=arrayField==null?root:root.path(arrayField);if(!rows.isArray())throw upstream();
   rows.forEach(all::add);if(rows.size()<100)return all;
  }throw upstream();
 }
 static String pagePath(String path,int page){return path+(path.contains("?")?"&":"?")+"per_page=100&page="+page;}
 static void safePath(String path){ validatePath(path,false); }
 private static void validatePath(String path,boolean generatedPagination){
  try {
   if(path==null||path.length()>8192||!path.matches("/[A-Za-z0-9_./?=&:+,%~@!-]+")||path.startsWith("//"))throw invalid();
   URI relative=URI.create(path);
   if(relative.isAbsolute()||relative.getRawAuthority()!=null||relative.getRawFragment()!=null)throw invalid();
   String raw=relative.getRawPath(),decoded=relative.getPath();
   // Encoded separators in the path can change routing. Encoded slash is safe in query values,
   // where branch names such as feature/main are passed to metadata endpoints.
   if(raw.toLowerCase(Locale.ROOT).matches(".*%(?:2f|5c|25).*"))throw invalid();
   if(decoded.contains("\\")||decoded.contains(":")||decoded.contains("//")||decoded.chars().anyMatch(Character::isISOControl))throw invalid();
   for(String segment:decoded.split("/"))if(segment.equals(".")||segment.equals(".."))throw invalid();
   String query=relative.getQuery();
   if(query!=null&&(query.contains("\\")||query.chars().anyMatch(Character::isISOControl)||relative.getRawQuery().toLowerCase(Locale.ROOT).contains("%25")))throw invalid();
   if(!generatedPagination&&query!=null&&query.matches("(?:^|.*&)(?:page|per_page)=.*"))throw invalid();
   URI target=URI.create("https://api.github.com"+path);
   if(!"api.github.com".equals(target.getHost())||target.getRawUserInfo()!=null||target.getPort()!=-1)throw invalid();
  }catch(RuntimeException e){throw invalid();}
 }
 // Pagination parameters are generated exclusively by this class; public callers cannot supply them.
 private Reply send(String method,String path,JsonNode body,String token){
  try{
   if(!Set.of("GET","POST","PUT","PATCH","DELETE").contains(method))throw invalid();
   validatePath(path,true);
   HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.github.com"+path)).timeout(Duration.ofSeconds(10))
    .header("Authorization","Bearer "+token).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").header("User-Agent","MeeTeam")
    .header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
   HttpResponse<String> response=http.send(req,HttpResponse.BodyHandlers.ofString());int status=response.statusCode();
   boolean emptyRepository=status==409&&"GET".equals(method)&&URI.create(path).getRawPath().matches("(?:/repositories/[1-9][0-9]*|/repos/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)/commits");
   if(!(Set.of(200,201,202,204).contains(status)||status==404||emptyRepository))throw upstream();
   String raw=response.body();if(raw!=null&&raw.length()>10_000_000)throw upstream();
   JsonNode json=raw==null||raw.isBlank()?mapper.nullNode():mapper.readTree(raw);return new Reply(status,json);
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw upstream();}catch(Exception e){throw upstream();}
 }
 String appJwt(){
  properties.requireEnabled();
  try{
   String pem=properties.getPrivateKey();
   if(pem==null||pem.length()>100_000)throw new IllegalArgumentException();
   pem=pem.trim();boolean pkcs1=pem.startsWith("-----BEGIN RSA PRIVATE KEY-----");
   String begin=pkcs1?"-----BEGIN RSA PRIVATE KEY-----":"-----BEGIN PRIVATE KEY-----";
   String end=pkcs1?"-----END RSA PRIVATE KEY-----":"-----END PRIVATE KEY-----";
   pem=pem.trim();if(!pem.startsWith(begin)||!pem.endsWith(end))throw new IllegalArgumentException();
   byte[] der=Base64.getDecoder().decode(pem.substring(begin.length(),pem.length()-end.length()).replaceAll("\\s",""));
   if(pkcs1)der=pkcs1ToPkcs8(der);
   PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
   long now=Instant.now().getEpochSecond();String header=enc("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
   String payload=enc(mapper.writeValueAsBytes(Map.of("iat",now-30,"exp",now+480,"iss",properties.getAppId())));String input=header+"."+payload;
   Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update(input.getBytes(StandardCharsets.US_ASCII));return input+"."+enc(signer.sign());
  }catch(Exception e){throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED);}
 }
 // PKCS#8 PrivateKeyInfo: version zero, rsaEncryption AlgorithmIdentifier, PKCS#1 OCTET STRING.
 // The JCA RSA KeyFactory validates the wrapped key; no key discovery or external process is used.
 private static byte[] pkcs1ToPkcs8(byte[] pkcs1){
  byte[] version={0x02,0x01,0x00};
  byte[] algorithm={0x30,0x0d,0x06,0x09,0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x01,0x05,0x00};
  byte[] octet=der(0x04,pkcs1);byte[] content=new byte[version.length+algorithm.length+octet.length];
  System.arraycopy(version,0,content,0,version.length);System.arraycopy(algorithm,0,content,version.length,algorithm.length);System.arraycopy(octet,0,content,version.length+algorithm.length,octet.length);
  return der(0x30,content);
 }
 private static byte[] der(int tag,byte[] content){
  int length=content.length,bytes=length<128?0:(32-Integer.numberOfLeadingZeros(length)+7)/8;
  byte[] result=new byte[1+(bytes==0?1:bytes+1)+length];result[0]=(byte)tag;int offset=1;
  if(bytes==0)result[offset++]=(byte)length;
  else{result[offset++]=(byte)(0x80|bytes);for(int shift=(bytes-1)*8;shift>=0;shift-=8)result[offset++]=(byte)(length>>>shift);}
  System.arraycopy(content,0,result,offset,length);return result;
 }
 private static String enc(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static CustomException upstream(){return new CustomException(ErrorCode.GITHUB_UPSTREAM_ERROR);}
 static CustomException invalid(){return new CustomException(ErrorCode.GITHUB_INVALID_REQUEST);}
}
