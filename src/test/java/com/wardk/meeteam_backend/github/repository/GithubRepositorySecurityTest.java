package com.wardk.meeteam_backend.github.repository;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.repository.*;
import com.wardk.meeteam_backend.domain.github.repository.GithubRepositoryStore.Context;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import com.wardk.meeteam_backend.domain.github.integration.GithubProjectLink;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class GithubRepositorySecurityTest {
 GithubRepositoryStore store=mock(GithubRepositoryStore.class);GithubAppClient client=mock(GithubAppClient.class);GithubAppProperties app=new GithubAppProperties();GithubAccountOAuthProperties account=new GithubAccountOAuthProperties();GithubInstallationState states=mock(GithubInstallationState.class);GithubInstallationOAuthClient oauth=mock(GithubInstallationOAuthClient.class);
 GithubRepositoryService service=new GithubRepositoryService(store,client,app,account,states,oauth);
 void config(){account.setEnabled(true);account.setClientId("id");account.setClientSecret("secret");account.setStateSecret(Base64.getEncoder().encodeToString(new byte[32]));account.setFrontOrigin("https://meeteam.test");account.setCallbackUri("https://meeteam.test/api/v1/github/oauth/callback");app.setAppId("123");}
 @Test void arbitraryInstallationRequiresSecondOAuthProof(){config();Context c=new Context(1,2,0,0,55,0);when(states.consume("state","install")).thenReturn(new GithubInstallationState.Validated(c,0,"verifier"));var issued=new GithubInstallationState.Issued("second","challenge");when(states.issue(c,"install-oauth",999)).thenReturn(issued);when(oauth.authorize(issued)).thenReturn("https://github.com/login/oauth/authorize?state=second");assertEquals("github.com",service.setup(999L,"state",2L).getHost());verify(store,never()).install(any(),anyLong(),any());verifyNoInteractions(client);}
 @Test void differentCurrentMemberCannotCompleteInstall(){config();Context c=new Context(1,2,0,0,55,0);when(states.consume("state","install-oauth")).thenReturn(new GithubInstallationState.Validated(c,999,"v"));assertTrue(service.finish("code","state",null,3L).toString().endsWith("installed=0"));verifyNoInteractions(oauth,client);verify(store,never()).install(any(),anyLong(),any());}
 @Test void invalidStateUsesSafeFallback(){config();when(states.consume(any(),any())).thenThrow(new IllegalArgumentException("secret"));assertEquals("https://meeteam.test/settings?github=failed",service.setup(1L,"evil",2L).toString());verifyNoInteractions(store,client,oauth);}
 @Test void unapprovedRepositoryNeverReachesApp(){Context c=new Context(1,2,0,0,55,0);when(store.context(1,2)).thenReturn(c);var link=new GithubProjectLink(1);link.setInstallationActive(true);link.setInstallationId(999L);when(store.get(1,2)).thenReturn(link);when(store.allowed(link)).thenReturn(Set.of(100L));assertThrows(RuntimeException.class,()->service.link(1,2,200));verifyNoInteractions(client);}
 @Test void listOnlyShowsExplicitAdminGrantsNotOtherProjects() throws Exception{var l=new GithubProjectLink(1);l.setInstallationActive(true);l.setInstallationId(999L);when(store.get(1,2)).thenReturn(l);when(store.allowed(l)).thenReturn(Set.of(1L,2L));when(store.taken(1)).thenReturn(Set.of(2L));var m=new ObjectMapper();when(client.paginate(999,"/installation/repositories","repositories")).thenReturn(List.of(m.readTree("{\"id\":1}"),m.readTree("{\"id\":2}"),m.readTree("{\"id\":3}")));assertEquals(1,service.repositories(1,2).size());}
 Context finishContext(){config();Context c=new Context(1,2,5,6,55,7);when(states.consume("state","install-oauth")).thenReturn(new GithubInstallationState.Validated(c,999,"v"));return c;}
 @Test void completeProofPersistsOnlyAdminGrantedIds() throws Exception{
  Context c=finishContext();when(oauth.prove("code","v",999,55)).thenReturn(Set.of(10L));
  when(client.getInstallation(999)).thenReturn(new ObjectMapper().readTree("{\"id\":999,\"app_id\":123,\"suspended_at\":null}"));
  assertTrue(service.finish("code","state",null,2L).toString().endsWith("installed=1"));verify(store).validate(c);verify(store).install(c,999,Set.of(10L));
 }
 @Test void suspendedInstallationNeverPersists() throws Exception{
  finishContext();when(oauth.prove("code","v",999,55)).thenReturn(Set.of(10L));when(client.getInstallation(999)).thenReturn(new ObjectMapper().readTree("{\"id\":999,\"app_id\":123,\"suspended_at\":\"2026-01-01T00:00:00Z\"}"));
  assertTrue(service.finish("code","state",null,2L).toString().endsWith("installed=0"));verify(store,never()).install(any(),anyLong(),any());
 }
 @Test void wrongAppNeverPersists() throws Exception{
  finishContext();when(oauth.prove("code","v",999,55)).thenReturn(Set.of(10L));when(client.getInstallation(999)).thenReturn(new ObjectMapper().readTree("{\"id\":999,\"app_id\":456,\"suspended_at\":null}"));service.finish("code","state",null,2L);verify(store,never()).install(any(),anyLong(),any());
 }
 @Test void identityProofFailureNeverPersists(){finishContext();when(oauth.prove("code","v",999,55)).thenThrow(new IllegalStateException("secret"));service.finish("code","state",null,2L);verifyNoInteractions(client);verify(store,never()).install(any(),anyLong(),any());}
 @Test void staleContextRejectedBeforeOAuth(){Context c=finishContext();doThrow(new IllegalStateException()).when(store).validate(c);service.finish("code","state",null,2L);verifyNoInteractions(oauth,client);verify(store,never()).install(any(),anyLong(),any());}
 @Test void longValidBranchIsNotTruncated() throws Exception{
  var c=new Context(1,2,0,0,55,0);var l=new GithubProjectLink(1);l.setInstallationActive(true);l.setInstallationId(999L);when(store.context(1,2)).thenReturn(c);when(store.get(1,2)).thenReturn(l);when(store.allowed(l)).thenReturn(Set.of(10L));
  var repo=new ObjectMapper().createObjectNode().put("id",10).put("full_name","o/r").put("html_url","https://github.com/o/r").put("default_branch","b".repeat(255));when(client.request(999,"GET","/repositories/10",null)).thenReturn(new GithubAppClient.Reply(200,repo));
  service.link(1,2,10);verify(store).link(c,999,repo);
 }
 @Test void oversizedBranchFailsBeforeDatabaseMutation() throws Exception{
  var c=new Context(1,2,0,0,55,0);var l=new GithubProjectLink(1);l.setInstallationActive(true);l.setInstallationId(999L);when(store.context(1,2)).thenReturn(c);when(store.get(1,2)).thenReturn(l);when(store.allowed(l)).thenReturn(Set.of(10L));
  var repo=new ObjectMapper().createObjectNode().put("id",10).put("full_name","o/r").put("html_url","https://github.com/o/r").put("default_branch","b".repeat(1025));when(client.request(999,"GET","/repositories/10",null)).thenReturn(new GithubAppClient.Reply(200,repo));
  assertThrows(RuntimeException.class,()->service.link(1,2,10));verify(store,never()).link(any(),anyLong(),any());
 }
}
