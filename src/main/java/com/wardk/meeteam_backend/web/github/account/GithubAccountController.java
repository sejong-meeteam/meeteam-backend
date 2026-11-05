package com.wardk.meeteam_backend.web.github.account;

import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.web.github.account.GithubAccountResponses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GithubAccountController {
    private final GithubAccountService accounts;
    private final GithubAccountOAuthService oauth;
    private static final String ACCOUNT = "/api/v1/members/me/github";

    @GetMapping(ACCOUNT)
    public ResponseEntity<Envelope<Account>> get(@AuthenticationPrincipal CustomSecurityUserDetails user) {
        return noStore(Envelope.ok(accounts.get(memberId(user))));
    }
    @GetMapping(ACCOUNT + "/authorize-url")
    public ResponseEntity<Envelope<AuthorizeUrl>> authorize(@AuthenticationPrincipal CustomSecurityUserDetails user,
            @RequestParam(defaultValue = "/settings") String redirectPath) {
        return noStore(Envelope.ok(new AuthorizeUrl(oauth.authorize(memberId(user), redirectPath))));
    }
    @DeleteMapping(ACCOUNT)
    public ResponseEntity<Envelope<Void>> disconnect(@AuthenticationPrincipal CustomSecurityUserDetails user) {
        accounts.disconnect(memberId(user));
        return noStore(Envelope.ok(null));
    }
    @GetMapping("/api/v1/github/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error,
            @AuthenticationPrincipal CustomSecurityUserDetails user) {
        var destination = oauth.callback(code, state, error, user == null ? null : user.getMemberId());
        return ResponseEntity.status(HttpStatus.FOUND).location(destination).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").build();
    }
    private static long memberId(CustomSecurityUserDetails user) {
        if (user == null || user.getMemberId() == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return user.getMemberId();
    }
    private static <T> ResponseEntity<Envelope<T>> noStore(Envelope<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").body(body);
    }
}
