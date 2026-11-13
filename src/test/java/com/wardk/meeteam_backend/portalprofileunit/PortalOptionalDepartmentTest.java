package com.wardk.meeteam_backend.global.auth.client;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PortalOptionalDepartmentTest {
 private static final String ID="24001234";
 private static String card(String id){return SejongPortalProfileParserTest.card("합성학생",id,"합성학과");}
 @Test void explicitSuccessRetrievesProfileWithOnePasswordSubmissionAndBoundedEnrichment() throws Exception {
  var s=new Scenario("var result = 'OK'",card(ID),200,200,-1);
  assertEquals("합성학과",s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only").department());s.finished(4);
  assertEquals(Timeout.ofSeconds(5),s.contexts.get(0).getRequestConfig().getResponseTimeout());
  assertEquals(Timeout.ofSeconds(5),s.contexts.get(0).getRequestConfig().getConnectionRequestTimeout());
 }
 @Test void explicitSuccessCanSurviveMissingOrMismatchedProfileWithoutAcceptingItsData() throws Exception {
  for(String html:List.of("<html>Login required</html>",card("24009999"))) {
   var s=new Scenario("var result = 'OK'",html,200,200,-1);
   assertNull(s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only"));s.finished(4);
  }
 }
 @Test void explicitSuccessCanSurviveProfileRedirectWithoutFollowingIt() throws Exception {
  var s=new Scenario("var result = 'OK'",card(ID),200,302,-1);
  assertNull(s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only"));s.finished(4);
 }
 @Test void explicitSuccessCanSurviveSsoFailure() throws Exception {
  var s=new Scenario("var result = 'OK'",card(ID),403,200,-1);
  assertNull(s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only"));s.finished(3);
 }
 @Test void credentialErrorsAndAmbiguityStillFailBeforeSso() throws Exception {
  for(String html:List.of("var result='erridpwd'","var result='pwdNeedChg'","var result='noaccess'","var result='OK';result='erridpwd'")) {
   var s=new Scenario(html,card(ID),200,200,-1);
   assertThrows(CustomException.class,()->s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only"));s.finished(2);
  }
 }
 @Test void unknownReplyCannotBecomeAuthenticatedWhenProfileFails() throws Exception {
  var s=new Scenario("<html>Unrecognized</html>","<html>Login required</html>",200,200,-1);
  assertEquals(ErrorCode.SEJONG_PORTAL_ERROR,assertThrows(CustomException.class,()->s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only")).getErrorCode());s.finished(4);
 }
 @Test void unknownReplyWithExactPrivateProfileProofCanAuthenticate() throws Exception {
  var s=new Scenario("<html>Unrecognized</html>",card(ID),200,200,-1);
  assertEquals(ID,s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only").studentId());s.finished(4);
 }
 @Test void transportFailureBeforeCredentialProofNeverFallsBack() throws Exception {
  var s=new Scenario("var result='OK'",card(ID),200,200,1);
  assertThrows(CustomException.class,()->s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only"));s.finished(2);
 }
 @Test void transportFailureAfterExplicitCredentialProofCanDegradeWithoutRetryingPassword() throws Exception {
  var s=new Scenario("var result='OK'",card(ID),200,200,2);
  assertNull(s.client.authenticateAndFetchOptionalProfile(ID,"synthetic-only"));s.finished(3);
 }
 @Test void newRegistrationProfileRemainsMandatory() throws Exception {
  var s=new Scenario("var result='OK'","<html>Login required</html>",200,200,-1);
  assertThrows(CustomException.class,()->s.client.authenticateAndFetchProfile(ID,"synthetic-only"));s.finished(4);
 }
 private static final class Scenario {
  final AtomicInteger calls=new AtomicInteger();final List<HttpClientContext> contexts=new ArrayList<>();final List<CookieStore> stores=new ArrayList<>();final SejongPortalClient client;
  Scenario(String login,String profile,int ssoStatus,int profileStatus,int failAt) throws Exception {
   var transport=mock(CloseableHttpClient.class);
   when(transport.execute(any(ClassicHttpRequest.class),any(HttpContext.class),PortalOptionalDepartmentTest.<String>anyHandler())).thenAnswer(i->{
    ClassicHttpRequest req=i.getArgument(0);HttpClientContext ctx=i.getArgument(1);HttpClientResponseHandler<String> handler=i.getArgument(2);int n=calls.getAndIncrement();
    assertEquals("https",req.getUri().getScheme());assertEquals("portal.sejong.ac.kr",req.getUri().getHost());assertFalse(ctx.getRequestConfig().isRedirectsEnabled());
    if(n==0){contexts.add(ctx);stores.add(ctx.getCookieStore());assertTrue(ctx.getCookieStore().getCookies().isEmpty());ctx.getCookieStore().addCookie(new BasicClientCookie("SYNTHETIC","synthetic-only"));}
    else assertSame(contexts.get(0),ctx);
    if(n==1){assertEquals("POST",req.getMethod());assertTrue(EntityUtils.toString(req.getEntity()).contains("mainLogin=Y"));}
    if(n>=2){assertEquals("GET",req.getMethod());assertEquals(n==2?"/comm/member/user/ssoLoginProc.do":"/user/index.do",req.getUri().getPath());if(ctx.getRequestConfig().getResponseTimeout().equals(Timeout.ofSeconds(5)))assertEquals(Timeout.ofSeconds(5),ctx.getRequestConfig().getConnectionRequestTimeout());}
    if(n==failAt)throw new IOException("synthetic transport detail must not be logged");
    var r=new BasicClassicHttpResponse(n==2?ssoStatus:n==3?profileStatus:200);r.setEntity(new StringEntity(n==1?login:n==3?profile:"<html>Public login</html>",ContentType.TEXT_HTML.withCharset(java.nio.charset.StandardCharsets.UTF_8)));r.setHeader("Location","https://unrelated.example.invalid/not-followed");return handler.handleResponse(r);
   });client=new SejongPortalClient(transport);
  }
  void finished(int n){assertEquals(n,calls.get());stores.forEach(s->assertTrue(s.getCookies().isEmpty()));contexts.forEach(c->assertNull(c.getCookieStore()));}
 }
 private static <T> HttpClientResponseHandler<T> anyHandler(){return any();}
}
