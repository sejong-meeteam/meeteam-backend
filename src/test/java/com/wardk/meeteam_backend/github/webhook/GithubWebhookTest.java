package com.wardk.meeteam_backend.github.webhook;
import com.fasterxml.jackson.databind.*;
import com.wardk.meeteam_backend.domain.github.integration.GithubWorkQueue;
import com.wardk.meeteam_backend.domain.github.repository.GithubAppProperties;
import com.wardk.meeteam_backend.domain.github.webhook.*;
import com.wardk.meeteam_backend.web.github.webhook.GithubWebhookController;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GithubWebhookTest {
 final ObjectMapper mapper=new ObjectMapper();final GithubWorkQueue queue=mock(GithubWorkQueue.class);
 final GithubAppProperties properties=new GithubAppProperties();final String delivery="12345678-abcd-1234-abcd-123456789abc";
 GithubWebhookIngest ingest;
 @BeforeEach void setup(){properties.setEnabled(true);properties.setWebhookSecret("test-only-webhook-secret");ingest=new GithubWebhookIngest(properties,mapper,new GithubWebhookNormalizer(mapper),queue);}
 String signature(byte[] body)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "sha256="+HexFormat.of().formatHex(m.doFinal(body));}
 @Test void requiresExactHmacOverOriginalBytes()throws Exception{byte[] b="{}".getBytes();ingest.verify(b,signature(b));for(String s:new String[]{"","sha1=123","sha256="+"0".repeat(64),"SHA256="+"0".repeat(64)})assertEquals(401,assertThrows(GithubWebhookException.class,()->ingest.verify(b,s)).status());assertEquals(401,assertThrows(GithubWebhookException.class,()->ingest.verify("{ }".getBytes(),signature(b))).status());}
 @Test void disabledRejectsBeforeStorage(){properties.setEnabled(false);assertEquals(503,assertThrows(GithubWebhookException.class,()->ingest.verify(new byte[0],null)).status());verifyNoInteractions(queue);}
 @Test void metadataNeverCopiesSourceEmailsMessagesOrBodies()throws Exception{
  JsonNode raw=mapper.readTree("""
   {"installation":{"id":8,"token":"secret-token"},"repository":{"id":3,"private":true},"pull_request":{"number":2,"title":"safe","body":"source-secret","diff_hunk":"code-secret","user":{"id":4,"login":"octocat","email":"email-secret"}},"review":{"id":10,"body":"review-secret","user":{"id":4}},"commits":[{"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","message":"message-secret","added":["path-secret"],"author":{"email":"author-secret","name":"real-secret","username":"octocat"}}]}
   """);String normalized=new GithubWebhookNormalizer(mapper).normalize("pull_request",raw,Instant.EPOCH).toString();for(String denied:new String[]{"secret","diff_hunk","email","message","added","token","body"})assertFalse(normalized.contains(denied));assertTrue(normalized.contains("octocat"));assertTrue(normalized.contains("received_at"));
 }
 @Test void preservesLongDefaultBranchRefAndNumericPushTime(){var raw=mapper.createObjectNode();String branch="a".repeat(1024);raw.put("ref","refs/heads/"+branch);raw.putObject("repository").put("default_branch",branch).put("pushed_at",123456789L);JsonNode normalized=new GithubWebhookNormalizer(mapper).normalize("push",raw,Instant.EPOCH);assertEquals(branch,normalized.path("repository").path("default_branch").asText());assertEquals("refs/heads/"+branch,normalized.path("ref").asText());assertEquals(123456789L,normalized.path("repository").path("pushed_at").asLong());raw.put("ref","refs/heads/"+branch+"x");assertThrows(GithubWebhookException.class,()->new GithubWebhookNormalizer(mapper).normalize("push",raw,Instant.EPOCH));}
 @Test void durableEnqueueAndDuplicateNoSecondWork()throws Exception{byte[] b="{\"installation\":{\"id\":1}}".getBytes();ingest.accept(b,signature(b),delivery,"push");verify(queue).enqueue(eq("WEBHOOK"),isNull(),isNull(),eq(0L),anyString(),eq("delivery:"+delivery));reset(queue);when(queue.exists("delivery:"+delivery)).thenReturn(true);ingest.accept(b,signature(b),delivery,"push");verify(queue,never()).enqueue(any(),any(),any(),anyLong(),any(),any());}
 @Test void malformedJsonAndHeadersRejectedUnknownEventIgnored()throws Exception{byte[] b="{".getBytes();assertEquals(400,assertThrows(GithubWebhookException.class,()->ingest.accept(b,signature(b),delivery,"push")).status());byte[] valid="{}".getBytes();assertThrows(GithubWebhookException.class,()->ingest.accept(valid,signature(valid),"bad","push"));ingest.accept(valid,signature(valid),delivery,"check_run");verifyNoInteractions(queue);}
 @Test void boundedStreamRejectsChunkedOversize(){var request=new MockHttpServletRequest(){@Override public long getContentLengthLong(){return -1;}};request.setContent(new byte[GithubWebhookController.MAX_BYTES+1]);assertEquals(413,new GithubWebhookController(ingest).receive(request).getStatusCode().value());verifyNoInteractions(queue);}
 @Test void errorsUseKoreanContractAndExplicitNullResult()throws Exception{for(int status:new int[]{400,401,413,503}){GithubWebhookIngest mocked=mock(GithubWebhookIngest.class);doThrow(new GithubWebhookException(status)).when(mocked).accept(any(),any(),any(),any());var response=new GithubWebhookController(mocked).receive(new MockHttpServletRequest());assertEquals(status,response.getStatusCode().value());JsonNode json=mapper.valueToTree(response.getBody());assertTrue(json.has("result"));assertTrue(json.path("result").isNull());assertFalse(json.path("message").asText().contains("rejected"));assertEquals(switch(status){case 400 -> "GITHUB400";case 401 -> "GITHUB_WEBHOOK_UNAUTHORIZED";case 413 -> "GITHUB_PAYLOAD_TOO_LARGE";default -> "GITHUB_NOT_CONFIGURED";},json.path("code").asText());}}
 @Test void uniqueRaceCheckedOutsideFailedIngest(){GithubWebhookIngest mock=mock(GithubWebhookIngest.class);doThrow(new DataIntegrityViolationException("race")).when(mock).accept(any(),any(),any(),any());when(mock.accepted(delivery)).thenReturn(true);MockHttpServletRequest r=new MockHttpServletRequest();r.addHeader("X-GitHub-Delivery",delivery);assertEquals(202,new GithubWebhookController(mock).receive(r).getStatusCode().value());}
}
