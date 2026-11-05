package com.wardk.meeteam_backend.web.github.collaboration;
import com.wardk.meeteam_backend.domain.github.collaboration.GithubCollaborationService;
import com.wardk.meeteam_backend.web.github.account.GithubAccountResponses.Envelope;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/projects/{projectId}/github/collaborators")
public class GithubCollaborationController {
 private final GithubCollaborationService service;
 @GetMapping public ResponseEntity<Envelope<List<GithubCollaboratorResponse>>> list(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails user){return response(service.list(projectId,id(user)));}
 @PostMapping("/{memberId}/invite") public ResponseEntity<Envelope<GithubCollaboratorResponse>> invite(@PathVariable long projectId,@PathVariable long memberId,@AuthenticationPrincipal CustomSecurityUserDetails user){return response(service.invite(projectId,id(user),memberId));}
 private static <T> ResponseEntity<Envelope<T>> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(Envelope.ok(value));}
 private Long id(CustomSecurityUserDetails user){if(user==null||user.getMemberId()==null)throw new CustomException(ErrorCode.UNAUTHORIZED);return user.getMemberId();}
}
