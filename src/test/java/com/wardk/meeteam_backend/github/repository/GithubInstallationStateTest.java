package com.wardk.meeteam_backend.github.repository;
import com.wardk.meeteam_backend.domain.github.repository.*;
import com.wardk.meeteam_backend.domain.github.repository.GithubRepositoryStore.Context;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import java.util.*;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class GithubInstallationStateTest {
 StringRedisTemplate redis=mock(StringRedisTemplate.class);@SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);GithubInstallationState states;
 Map<String,String> stored=new HashMap<>();Context context=new Context(1,2,3,4,5,6);
 @BeforeEach void init(){var p=new GithubAccountOAuthProperties();p.setEnabled(true);p.setClientId("id");p.setClientSecret("secret");p.setFrontOrigin("https://meeteam.test");p.setCallbackUri("https://meeteam.test/api/v1/github/oauth/callback");p.setStateSecret(Base64.getEncoder().encodeToString(new byte[32]));when(redis.opsForValue()).thenReturn(values);when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenAnswer(a->{stored.put(a.getArgument(0),a.getArgument(1));return true;});when(values.getAndDelete(anyString())).thenAnswer(a->stored.remove(a.getArgument(0)));states=new GithubInstallationState(p,redis);}
 @Test void signedContextRoundTripsAndReplayFails(){var issued=states.issue(context,"install-oauth",88);var verified=states.consume(issued.state(),"install-oauth");assertEquals(context,verified.context());assertEquals(88,verified.installationId());assertEquals(43,issued.challenge().length());assertThrows(RuntimeException.class,()->states.consume(issued.state(),"install-oauth"));}
 @Test void purposeMismatchDoesNotConsumeNonce(){var issued=states.issue(context,"install",0);assertThrows(RuntimeException.class,()->states.consume(issued.state(),"install-oauth"));verify(values,never()).getAndDelete(anyString());assertEquals(context,states.consume(issued.state(),"install").context());}
 @Test void tamperingDoesNotConsumeNonce(){var issued=states.issue(context,"install",0);String state=issued.state();int middle=state.indexOf('.')+5;String changed=state.substring(0,middle)+(state.charAt(middle)=='A'?'B':'A')+state.substring(middle+1);assertThrows(RuntimeException.class,()->states.consume(changed,"install"));verify(values,never()).getAndDelete(anyString());}
}
