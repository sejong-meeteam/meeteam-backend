package com.wardk.meeteam_backend.github.repository;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.repository.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.*;
import java.security.*;
import java.util.*;
import org.mockito.ArgumentCaptor;

class GithubAppClientTest {
 ObjectMapper mapper=new ObjectMapper();HttpClient http;GithubAppClient client;
 @BeforeEach void init() throws Exception{
  http=mock(HttpClient.class);GithubAppProperties p=new GithubAppProperties();p.setEnabled(true);p.setAppId("123");p.setSlug("meeteam");
  var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);p.setPrivateKey("-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(gen.generateKeyPair().getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----");client=new GithubAppClient(p,mapper,http);
 }
 @SuppressWarnings("unchecked") HttpResponse<String> reply(int status,String body){HttpResponse<String> r=mock(HttpResponse.class);when(r.statusCode()).thenReturn(status);when(r.body()).thenReturn(body);return r;}
 @Test void signsBoundedAppJwtAndUsesEphemeralToken() throws Exception{
  doReturn(reply(201,"{\"token\":\"ghs_local\"}"),reply(200,"{\"id\":55}")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
  assertEquals(55,client.request(42,"GET","/repositories/55",null).body().path("id").asLong());
  var cap=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(2)).send(cap.capture(),any(HttpResponse.BodyHandler.class));
  var app=cap.getAllValues().get(0);String jwt=app.headers().firstValue("Authorization").orElseThrow().substring(7);
  JsonNode claims=mapper.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));assertTrue(claims.path("exp").asLong()-claims.path("iat").asLong()<=540);
  assertEquals("https://api.github.com/app/installations/42/access_tokens",app.uri().toString());assertEquals(10,app.timeout().orElseThrow().toSeconds());assertEquals("Bearer ghs_local",cap.getAllValues().get(1).headers().firstValue("Authorization").orElseThrow());
 }
 @Test void rejectsUnsafePathsBeforeNetwork(){for(String path:List.of("https://evil.test","//evil.test/x","/repos/a/../b","/repos/a%2fb","/repos/a\\b","/installation/repositories?page=2","/repos/o/%2e%2e/r","/repos/o/r?sha=a%0d%0aevil","/repos/o/r?sha=%252f","/repos/o/r?sha=%zz"))assertThrows(CustomException.class,()->client.request(1,"GET",path,null));verifyNoInteractions(http);}
 @Test void refusesRedirectAndSanitizesFailure() throws Exception{doReturn(reply(302,"secret-upstream-token")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));var e=assertThrows(CustomException.class,()->client.getInstallation(1));assertFalse(e.toString().contains("secret-upstream-token"));assertNull(e.getCause());}
 @Test void paginationDoesNotSilentlyIgnoreSecondPageFailure() throws Exception{
  var a=mapper.createArrayNode();for(int n=0;n<100;n++)a.addObject().put("id",n+1);
  doReturn(reply(201,"{\"token\":\"local\"}"),reply(200,a.toString()),reply(201,"{\"token\":\"local\"}"),reply(503,"private failure")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));assertThrows(CustomException.class,()->client.paginate(1,"/installation/repositories",null));verify(http,times(4)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
 }
 @Test void delete404IsIdempotent() throws Exception{doReturn(reply(201,"{\"token\":\"local\"}"),reply(404,"{}")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));assertEquals(404,client.request(1,"DELETE","/repos/o/r/collaborators/person",null).status());}
 @Test void supportsEncodedSlashAndUnicodeBranchQuery() throws Exception{
  String path="/repos/o/r/commits?sha=feature%2F%ED%95%9C%EA%B8%80";
  doReturn(reply(201,"{\"token\":\"local\"}"),reply(200,"[]")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
  assertTrue(client.request(1,"GET",path,null).body().isArray());
  var cap=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(2)).send(cap.capture(),any(HttpResponse.BodyHandler.class));
  assertEquals(path,cap.getAllValues().get(1).uri().getRawPath()+"?"+cap.getAllValues().get(1).uri().getRawQuery());
 }
 @Test void acceptsSyntheticPkcs1PemAndProducesValidRsaSignature() throws Exception{
  var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
  var rsa=(java.security.interfaces.RSAPrivateCrtKey)pair.getPrivate();var content=new java.io.ByteArrayOutputStream();
  for(java.math.BigInteger n:List.of(java.math.BigInteger.ZERO,rsa.getModulus(),rsa.getPublicExponent(),rsa.getPrivateExponent(),rsa.getPrimeP(),rsa.getPrimeQ(),rsa.getPrimeExponentP(),rsa.getPrimeExponentQ(),rsa.getCrtCoefficient()))content.write(testDer(2,n.toByteArray()));
  byte[] pkcs1=testDer(0x30,content.toByteArray());GithubAppProperties p=new GithubAppProperties();p.setEnabled(true);p.setAppId("123");p.setSlug("meeteam");p.setPrivateKey("-----BEGIN RSA PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(pkcs1)+"\n-----END RSA PRIVATE KEY-----");
  var pkcs1Client=new GithubAppClient(p,mapper,http);doReturn(reply(200,"{\"id\":42}")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));pkcs1Client.getInstallation(42);
  var cap=ArgumentCaptor.forClass(HttpRequest.class);verify(http).send(cap.capture(),any(HttpResponse.BodyHandler.class));String jwt=cap.getValue().headers().firstValue("Authorization").orElseThrow().substring(7);String[] parts=jwt.split("\\.");
  Signature verify=Signature.getInstance("SHA256withRSA");verify.initVerify(pair.getPublic());verify.update((parts[0]+"."+parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));assertTrue(verify.verify(Base64.getUrlDecoder().decode(parts[2])));
 }
 @Test void rejectsMalformedPkcs1WithoutLeakingKey() throws Exception{
  GithubAppProperties p=new GithubAppProperties();p.setEnabled(true);p.setAppId("123");p.setSlug("meeteam");p.setPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nU0VDUkVU\n-----END RSA PRIVATE KEY-----");
  var bad=new GithubAppClient(p,mapper,http);var failure=assertThrows(CustomException.class,()->bad.getInstallation(1));assertFalse(failure.toString().contains("U0VDUkVU"));assertNull(failure.getCause());verifyNoInteractions(http);
 }
 private static byte[] testDer(int tag,byte[] content) throws java.io.IOException{
  var out=new java.io.ByteArrayOutputStream();out.write(tag);int len=content.length;if(len<128)out.write(len);else if(len<=255){out.write(0x81);out.write(len);}else{out.write(0x82);out.write(len>>>8);out.write(len);}out.write(content);return out.toByteArray();
 }
 @Test void emptyRepository409PassesThroughOnlyForCommitMetadata() throws Exception{
  doReturn(reply(201,"{\"token\":\"local\"}"),reply(409,"{\"message\":\"Git Repository is empty.\"}"),reply(201,"{\"token\":\"local\"}"),reply(409,"{}"))
   .when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
  assertEquals(409,client.request(1,"GET","/repositories/42/commits?sha=feature%2Fmain",null).status());
  assertThrows(CustomException.class,()->client.request(1,"PUT","/repos/o/r/collaborators/member",null));
 }
 @Test void namedEmptyRepository409AlsoPassesThrough() throws Exception{
  doReturn(reply(201,"{\"token\":\"local\"}"),reply(409,"{}")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
  assertEquals(409,client.request(1,"GET","/repos/owner/repo/commits",null).status());
 }
}
