package com.wardk.meeteam_backend.github.integration;

import com.wardk.meeteam_backend.domain.github.activity.GithubActivityQuery;
import com.wardk.meeteam_backend.domain.github.repository.GithubRepositoryService;
import com.wardk.meeteam_backend.domain.github.collaboration.GithubCollaborationService;
import com.wardk.meeteam_backend.domain.github.webhook.GithubWebhookIngest;
import com.wardk.meeteam_backend.domain.github.integration.GithubProjectLink;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.auth.repository.TokenBlacklistRepository;
import com.wardk.meeteam_backend.global.auth.service.CustomUserDetailsService;
import com.wardk.meeteam_backend.global.config.*;
import com.wardk.meeteam_backend.global.exception.*;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.github.activity.GithubActivityController;
import com.wardk.meeteam_backend.web.github.repository.GithubRepositoryController;
import com.wardk.meeteam_backend.web.github.collaboration.GithubCollaborationController;
import com.wardk.meeteam_backend.web.github.webhook.GithubWebhookController;
import com.wardk.meeteam_backend.web.github.integration.GithubRequestExceptionHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={GithubRepositoryController.class,GithubActivityController.class,GithubCollaborationController.class,GithubWebhookController.class})
@Import({SecurityConfig.class,SecurityUrls.class,RestAccessDeniedHandler.class,RestAuthenticationEntryPoint.class,
 GithubRequestExceptionHandler.class,GithubApiSecurityTest.CorsConfig.class})
class GithubApiSecurityTest {
 @Autowired MockMvc mvc;
 @MockBean GithubRepositoryService repository;
 @MockBean GithubActivityQuery activity;
 @MockBean GithubCollaborationService collaboration;
 @MockBean GithubWebhookIngest ingest;
 @MockBean com.wardk.meeteam_backend.domain.file.service.S3ImageUrlResolver images;
 @MockBean JwtUtil jwt;@MockBean CustomUserDetailsService users;@MockBean TokenBlacklistRepository blacklist;
 static final String ROOT="/api/v1/projects/1/github";
 @TestConfiguration static class CorsConfig{
  @Bean CorsProperties corsProperties(){var p=new CorsProperties();p.setAllowedOrigins(List.of("https://front.example.invalid"));p.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));p.setAllowedHeaders(List.of("*"));p.setExposedHeaders(List.of());return p;}
 }
 void authenticate(){var member=Member.createForTest("unit@example.invalid","테스트");ReflectionTestUtils.setField(member,"id",7L);
  when(jwt.getCategory("unit-token")).thenReturn(JwtUtil.ACCESS_CATEGORY);when(jwt.getMemberId("unit-token")).thenReturn(7L);
  when(jwt.getUsername("unit-token")).thenReturn(member.getEmail());when(users.loadUserByUsername(member.getEmail())).thenReturn(new CustomSecurityUserDetails(member));}
 @ParameterizedTest @ValueSource(strings={"","/install-url","/repositories","/progress","/weekly-report?weekStart=2026-11-02","/collaborators"})
 void privateReadsRequireLogin(String suffix)throws Exception{
  mvc.perform(get(ROOT+suffix)).andExpect(status().isUnauthorized());verifyNoInteractions(repository,activity,collaboration);
 }
 @Test void allMutationsRequireLoginBeforeParsingOrRemoteCalls()throws Exception{
  mvc.perform(put(ROOT+"/repository").contentType(MediaType.APPLICATION_JSON).content("{\"repositoryId\":123}")).andExpect(status().isUnauthorized());
  mvc.perform(delete(ROOT+"/repository")).andExpect(status().isUnauthorized());
  mvc.perform(patch(ROOT+"/settings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(post(ROOT+"/collaborators/8/invite")).andExpect(status().isUnauthorized());verifyNoInteractions(repository,collaboration);
 }
 @Test void anonymousPublicActivityKeepsExplicitNull()throws Exception{
  mvc.perform(get(ROOT+"/activity")).andExpect(status().isOk()).andExpect(content().string(containsString("\"result\":null")))
   .andExpect(header().string("Cache-Control","no-store"));verify(activity).activity(1,null);
 }
 @Test void hiddenProjectIsNotPubliclyDisclosed()throws Exception{
  when(activity.activity(1,null)).thenThrow(new CustomException(ErrorCode.PROJECT_NOT_FOUND));
  mvc.perform(get(ROOT+"/activity")).andExpect(status().isNotFound()).andExpect(jsonPath("$.result").value(nullValue()));
 }
 @Test void ordinaryUserCannotSeeMemberOnlyReport()throws Exception{
  authenticate();when(activity.progress(1,7L)).thenThrow(new CustomException(ErrorCode.GITHUB_FORBIDDEN,"프로젝트 팀원만 볼 수 있어요."));
  mvc.perform(get(ROOT+"/progress").header("Authorization","Bearer unit-token"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GITHUB403"))
   .andExpect(jsonPath("$.message").value("프로젝트 팀원만 볼 수 있어요."));
 }
 @Test void uninstalledConnectionMatchesExactFrontendShape()throws Exception{
  authenticate();when(repository.get(1,7)).thenReturn(new GithubProjectLink(1));
  mvc.perform(get(ROOT).header("Authorization","Bearer unit-token")).andExpect(status().isOk())
   .andExpect(jsonPath("$.result.isAppInstalled").value(false)).andExpect(jsonPath("$.result.repository").value(nullValue()))
   .andExpect(jsonPath("$.result.settings.autoInviteOnApproval").value(true)).andExpect(jsonPath("$.result.settings.autoRevokeOnRemoval").value(true));
 }
 @Test void webhookUsesSignatureLayerRatherThanMeeTeamJwt()throws Exception{
  mvc.perform(post("/api/v1/github/webhooks").contentType(MediaType.APPLICATION_JSON).content("{}")
   .header("X-GitHub-Delivery","00000000-0000-0000-0000-000000000001").header("X-GitHub-Event","push").header("X-Hub-Signature-256","signature"))
   .andExpect(status().isAccepted());verify(ingest).accept(any(),eq("signature"),anyString(),eq("push"));
 }
 @Test void setupIsPublicButPassesInitiatingCookieIdentityForServiceVerification()throws Exception{
  authenticate();when(repository.setup(123L,"signed-state",7L)).thenReturn(URI.create("https://github.com/login/oauth/authorize"));
  mvc.perform(get("/api/v1/github/app/setup").param("installation_id","123").param("state","signed-state")
   .cookie(new Cookie(JwtUtil.ACCESS_COOKIE_NAME,"unit-token"))).andExpect(status().isFound());verify(repository).setup(123L,"signed-state",7L);
 }
 @Test void secondSetupCallbackWithoutSessionCannotImpersonateStateMember()throws Exception{
  when(repository.finish("code","state",null,null)).thenReturn(URI.create("https://front.example.invalid/settings?github=failed"));
  mvc.perform(get("/api/v1/github/app/setup/oauth/callback").param("code","code").param("state","state"))
   .andExpect(status().isFound()).andExpect(redirectedUrl("https://front.example.invalid/settings?github=failed"));verify(repository).finish("code","state",null,null);
 }
 @Test void malformedDateAndMissingWeekAreKorean400NotLeaked500()throws Exception{
  authenticate();for(String suffix:List.of("/weekly-report?weekStart=not-a-date","/weekly-report"))
   mvc.perform(get(ROOT+suffix).header("Authorization","Bearer unit-token")).andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.code").value("GITHUB400")).andExpect(jsonPath("$.result").value(nullValue()));
 }
 @Test void nullJsonBodiesAreKoreanClientErrorsNotServerFailures()throws Exception{
  authenticate();
  mvc.perform(put(ROOT+"/repository").header("Authorization","Bearer unit-token").contentType(MediaType.APPLICATION_JSON).content("null"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GITHUB400"));
  mvc.perform(patch(ROOT+"/settings").header("Authorization","Bearer unit-token").contentType(MediaType.APPLICATION_JSON).content("null"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GITHUB400"));
  verifyNoInteractions(repository);
 }
 @Test void unexpectedDatabaseErrorsNeverExposeSqlOrUpstreamContent()throws Exception{
  authenticate();when(repository.get(1,7)).thenThrow(new IllegalStateException("sensitive SQL or OAuth payload"));
  mvc.perform(get(ROOT).header("Authorization","Bearer unit-token")).andExpect(status().isInternalServerError())
   .andExpect(jsonPath("$.code").value("COMMON500")).andExpect(content().string(not(containsString("sensitive"))));
 }
}
