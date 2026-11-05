package com.wardk.meeteam_backend.web.github.activity;
import com.wardk.meeteam_backend.domain.github.activity.GithubActivityQuery;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.github.account.GithubAccountResponses.Envelope;
import com.wardk.meeteam_backend.web.github.activity.GithubActivityResponses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/projects/{projectId}/github")
public class GithubActivityController {
 private final GithubActivityQuery queries;
 @GetMapping("/activity") public ResponseEntity<Envelope<Activity>> activity(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails user){return response(queries.activity(projectId,id(user)));}
 @GetMapping("/progress") public ResponseEntity<Envelope<Progress>> progress(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails user){return response(queries.progress(projectId,id(user)));}
 @GetMapping("/weekly-report") public ResponseEntity<Envelope<WeeklyReport>> weekly(@PathVariable long projectId,@AuthenticationPrincipal CustomSecurityUserDetails user,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate weekStart){return response(queries.weekly(projectId,id(user),weekStart));}
 private static Long id(CustomSecurityUserDetails user){return user==null?null:user.getMemberId();}
 private static <T> ResponseEntity<Envelope<T>> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Envelope.ok(value));}
}
