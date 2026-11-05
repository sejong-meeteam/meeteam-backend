package com.wardk.meeteam_backend.global.auth.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final MemberRepository memberRepository;
  @org.springframework.beans.factory.annotation.Value("${admin-account.enabled:false}")
  private boolean adminAccountEnabled;

  @Override
  public CustomSecurityUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

    // DB에서 활성 회원만 조회 (탈퇴 회원 제외)
    Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
        .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없거나 탈퇴한 회원입니다: " + email));

    if (member.isLocalAdminAccount() && !adminAccountEnabled) {
      throw new UsernameNotFoundException("관리자 전용 로그인이 비활성화되어 있습니다.");
    }
    if (member.isSuspended()) {
      throw new org.springframework.security.authentication.DisabledException("이용 정지된 회원입니다.");
    }

    // UserDetails에 담아서 return 하면 AuthenticationManager가 검증함
    return new CustomSecurityUserDetails(member);
  }
}
