package com.wardk.meeteam_backend.web.github.webhook;
import com.wardk.meeteam_backend.domain.github.webhook.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.response.ErrorResponse;
@RestController
public class GithubWebhookController {
 public static final int MAX_BYTES=1024*1024;
 private final GithubWebhookIngest ingest;
 public GithubWebhookController(GithubWebhookIngest ingest){this.ingest=ingest;}
 @PostMapping("/api/v1/github/webhooks") public ResponseEntity<?> receive(HttpServletRequest request){
  String delivery=request.getHeader("X-GitHub-Delivery");
  try{
   if(request.getContentLengthLong()>MAX_BYTES)throw new GithubWebhookException(413);
   byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new GithubWebhookException(413);
   ingest.accept(bytes,request.getHeader("X-Hub-Signature-256"),delivery,request.getHeader("X-GitHub-Event"));
   return ResponseEntity.accepted().build();
  }catch(DataIntegrityViolationException race){if(ingest.accepted(delivery))return ResponseEntity.accepted().build();throw race;}
  catch(GithubWebhookException e){return error(e.status());}
  catch(IOException e){return error(400);}
 }
 private ResponseEntity<ErrorResponse> error(int status){
  ErrorCode code=switch(status){case 401 -> ErrorCode.GITHUB_WEBHOOK_UNAUTHORIZED;case 413 -> ErrorCode.GITHUB_PAYLOAD_TOO_LARGE;case 503 -> ErrorCode.GITHUB_NOT_CONFIGURED;default -> ErrorCode.GITHUB_INVALID_REQUEST;};
  return ResponseEntity.status(code.getStatus()).body(ErrorResponse.getResponse(code.getCode(),code.getMessage()));
 }
}
