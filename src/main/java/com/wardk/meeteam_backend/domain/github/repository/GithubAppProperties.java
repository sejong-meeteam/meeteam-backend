package com.wardk.meeteam_backend.domain.github.repository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
@Component @ConfigurationProperties(prefix="integration.github.app") @Getter @Setter
public class GithubAppProperties {
 private boolean enabled;
 private String appId, slug, privateKey, webhookSecret;
 public void requireEnabled() {
  if (!enabled || appId==null || !appId.matches("[1-9][0-9]*") || slug==null || !slug.matches("[A-Za-z0-9-]+") || privateKey==null || privateKey.isBlank())
   throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED);
 }
}
