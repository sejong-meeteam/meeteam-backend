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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Opt-in local administrator identity; never authenticates against the student portal. */
@Service
@ConditionalOnProperty(name = "admin-account.enabled", havingValue = "true")
public class AdminAccountService implements ApplicationRunner {
    public static final String EMAIL = "meeteam-admin@example.invalid";
    private final MemberRepository members;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwt;
    private final String username;
    private final String password;
    private final String dummyHash;
    private final boolean configuredHash;
    private long attemptWindow = System.nanoTime();
    private int attempts;

    public AdminAccountService(MemberRepository members, BCryptPasswordEncoder encoder, JwtUtil jwt,
                               String username, String password) {
        this(members, encoder, jwt, username, password, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminAccountService(MemberRepository members, BCryptPasswordEncoder encoder, JwtUtil jwt,
                               @Value("${admin-account.username}") String username,
                               @Value("${admin-account.password:}") String password,
                               @Value("${admin-account.password-hash:}") String passwordHash) {
        if (username == null || !username.matches("^99[0-9]{6}$")
                || TestAccountService.USERNAME.equals(username)) {
            throw new IllegalArgumentException("Administrator username must use an unreserved eight-digit 99-prefixed identity");
        }
        boolean hasHash = passwordHash != null && !passwordHash.isBlank();
        boolean hasPassword = password != null && !password.isEmpty();
        if (hasHash) {
            if (hasPassword || !passwordHash.matches("\\$2[aby]\\$(10|11|12|13|14)\\$[./A-Za-z0-9]{53}")) {
                throw new IllegalArgumentException("Administrator requires exactly one valid credential source");
            }
        } else if (password == null || password.isBlank() || password.length() < 20
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Administrator password requires at least 20 characters and at most 72 UTF-8 bytes");
        }
        this.members = members;
        this.encoder = encoder;
        this.jwt = jwt;
        this.username = username;
        this.password = hasHash ? null : password;
        this.configuredHash = hasHash;
        this.dummyHash = hasHash ? passwordHash : encoder.encode(password);
    }

    public boolean matchesUsername(String candidate) {
        return username.equals(candidate);
    }

    /**
     * Serializable isolation protects the absent-identity read as well as existing rows.
     * Concurrent bootstraps may deadlock; the losing transaction must fail closed, not retry by overwriting.
     * Actual serialization/deadlock behavior depends on the deployed database.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void run(ApplicationArguments args) {
        if (!members.findAllByStudentIdForUpdate(username).isEmpty()) {
            throw bootstrapConflict();
        }
        var existing = members.findByEmail(EMAIL);
        if (existing.isEmpty()) {
            members.save(Member.createForLocalAdmin(EMAIL, dummyHash));
            return;
        }
        Member member = existing.get();
        // Suspension remains untouched; never restore privileges, revive or reset credentials.
        if (!isAdminIdentity(member) || member.getPassword() == null
                || !(configuredHash
                    ? java.security.MessageDigest.isEqual(dummyHash.getBytes(StandardCharsets.US_ASCII),
                        member.getPassword().getBytes(StandardCharsets.US_ASCII))
                    : encoder.matches(password, member.getPassword()))) {
            throw bootstrapConflict();
        }
    }

    @Transactional
    public TokenExchangeResult login(String candidate, String submittedPassword) {
        checkAttemptLimit();
        Member member = matchesUsername(candidate)
                ? members.findByEmailAndDeletedAtIsNull(EMAIL).orElse(null) : null;
        boolean validIdentity = member != null && isAdminIdentity(member) && member.getPassword() != null;
        boolean validInput = submittedPassword != null
                && submittedPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
        // Invalid/missing identities still perform BCrypt work without consulting their stored hash.
        boolean matches = encoder.matches(validInput ? submittedPassword : "",
                validIdentity && validInput ? member.getPassword() : dummyHash);
        if (!validInput || !validIdentity || !matches) {
            throw new CustomException(ErrorCode.BAD_CREDENTIALS);
        }
        AuthService.requireActiveLogin(member);
        member.recordLogin(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        return new TokenExchangeResult(jwt.createAccessToken(member), jwt.createRefreshToken(member));
    }

    private boolean isAdminIdentity(Member member) {
        return EMAIL.equals(member.getEmail()) && member.getDeletedAt() == null
                && !member.isAdminWithdrawn() && member.getStudentId() == null
                && "local-admin".equals(member.getProvider()) && member.getRole() == UserRole.ADMIN;
    }

    private IllegalStateException bootstrapConflict() {
        return new IllegalStateException("Reserved administrator identity conflicts with existing data; no account was modified");
    }

    // Per-instance fixed monotonic one-minute window; successes also consume an attempt.
    private synchronized void checkAttemptLimit() {
        long now = System.nanoTime();
        if (now - attemptWindow >= 60_000_000_000L) {
            attemptWindow = now;
            attempts = 0;
        }
        if (attempts >= 5) {
            throw new CustomException(ErrorCode.BAD_CREDENTIALS);
        }
        attempts++;
    }
}
