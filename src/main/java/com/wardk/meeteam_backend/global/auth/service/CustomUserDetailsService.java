package com.wardk.meeteam_backend.global.auth.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.auth.dto.CustomSecurityUserDetails;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final MemberRepository memberRepository;

  @Override
  public CustomSecurityUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

    // DB에서 활성 회원만 조회 (탈퇴 회원 제외)
    Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
        .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없거나 탈퇴한 회원입니다: " + email));

    // UserDetails에 담아서 return 하면 AuthenticationManager가 검증함
    return new CustomSecurityUserDetails(member);
  }
}
