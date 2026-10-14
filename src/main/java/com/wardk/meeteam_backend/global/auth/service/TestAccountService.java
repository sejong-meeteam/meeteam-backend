package com.wardk.meeteam_backend.global.auth.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.entity.UserRole;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.dto.TokenExchangeResult;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/** Explicitly opt-in, non-production login for one reserved test account. */
@Service
@Profile("test & !prod & !production")
@ConditionalOnProperty(name = "test-account.enabled", havingValue = "true")
public class TestAccountService implements ApplicationRunner {
    public static final String USERNAME = "99990001";
    // Internal identity for the existing email-based member/JWT model. Not used as the login ID.
    public static final String EMAIL = "meeteam-test@example.invalid";
    private final MemberRepository members;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwt;
    private final String password;
    private final String dummyHash;
    private long attemptWindow = System.nanoTime();
    private int attempts;

    public TestAccountService(MemberRepository members, BCryptPasswordEncoder encoder, JwtUtil jwt,
                              @Value("${test-account.password}") String password) {
        if (password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Test account requires a password of at least 16 characters and at most 72 UTF-8 bytes");
        }
        this.members = members;
        this.encoder = encoder;
        this.jwt = jwt;
        this.password = password;
        this.dummyHash = encoder.encode(password);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var existing = members.findByEmail(EMAIL);
        if (existing.isEmpty()) {
            members.save(Member.createForTest(EMAIL, "테스트 사용자", dummyHash));
            return;
        }
        // Never overwrite or assume control of an existing account.
        Member member = existing.get();
        if (!isTestMember(member) || member.getPassword() == null
                || !encoder.matches(password, member.getPassword())) {
            throw new IllegalStateException("Reserved test account conflicts with existing data; no account was modified");
        }
    }

    @Transactional(readOnly = true)
    public TokenExchangeResult login(String username, String submittedPassword) {
        checkAttemptLimit();
        if (submittedPassword == null || submittedPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new CustomException(ErrorCode.BAD_CREDENTIALS);
        }
        Member member = USERNAME.equals(username) ? members.findByEmailAndDeletedAtIsNull(EMAIL).orElse(null) : null;
        String hash = member != null && member.getPassword() != null ? member.getPassword() : dummyHash;
        boolean matches = encoder.matches(submittedPassword, hash);
        if (member == null || !isTestMember(member) || !matches) {
            throw new CustomException(ErrorCode.BAD_CREDENTIALS);
        }
        return new TokenExchangeResult(jwt.createAccessToken(member), jwt.createRefreshToken(member));
    }

    private boolean isTestMember(Member member) {
        return EMAIL.equals(member.getEmail()) && member.getDeletedAt() == null
                && member.getStudentId() == null && member.getProvider() == null
                && member.getRole() == UserRole.USER;
    }

    // Limit this shared test account to ten attempts per minute, including successes.
    private synchronized void checkAttemptLimit() {
        long now = System.nanoTime();
        if (now - attemptWindow >= 60_000_000_000L) {
            attemptWindow = now;
            attempts = 0;
        }
        if (++attempts > 10) {
            throw new CustomException(ErrorCode.BAD_CREDENTIALS);
        }
    }
}
