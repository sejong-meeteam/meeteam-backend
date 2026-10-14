package com.wardk.meeteam_backend.acceptance.cucumber.factory;


import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 테스트용 Member 데이터 생성 Factory
 */
@Component
public class MemberFactory {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JobPositionRepository jobPositionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 기본 회원 생성
     */
    public Member create(String name) {
        return create(name, toEmail(name), "password123");
    }

    /**
     * 이메일 지정 회원 생성
     */
    public Member create(String name, String email) {
        return create(name, email, "password123");
    }

    /**
     * 상세 정보 지정 회원 생성
     */
    public Member create(String name, String email, String password) {
        Member member = Member.createForTest(email, name, passwordEncoder.encode(password));
        jobPositionRepository.findByCode(JobPositionCode.JAVA_SPRING)
                .ifPresent(member::addJobPosition);
        return memberRepository.save(member);
    }

    // OAuth 기능 제거됨 (2026-04-27) - 일반 회원으로 대체
    public Member createOAuthMember(String name, String email, String provider, String providerId) {
        // OAuth 회원 대신 일반 회원으로 생성
        return create(name, email, "oauth-password");
    }

    /**
     * 이름으로 회원 조회 또는 생성
     */
    public Member findOrCreate(String name) {
        String email = toEmail(name);
        return memberRepository.findByEmail(email)
                .orElseGet(() -> create(name, email));
    }

    /**
     * 이름을 이메일로 변환
     */
    private String toEmail(String name) {
        return switch (name) {
            case "홍길동" -> "hong@example.com";
            case "김철수" -> "kim@example.com";
            case "이영희" -> "lee@example.com";
            case "박지민" -> "park@example.com";
            case "새사용자" -> "new@example.com";
            default -> "user" + Math.abs(name.hashCode()) + "@example.com";
        };
    }
}