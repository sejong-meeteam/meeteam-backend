package com.wardk.meeteam_backend.web.auth.dto;



import com.wardk.meeteam_backend.domain.member.entity.Member;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Getter
public class CustomSecurityUserDetails implements UserDetails {

  private final Member member;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // Spring Security는 'ROLE_' 접두사를 기준으로 권한을 인식합니다.
    return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
  }

  @Override
  public String getPassword() {
    return member.getPassword();
  }

  @Override
  public String getUsername() {
    return member.getEmail();
  }

  public Long getMemberId() {
    return member.getId();
  }
}
