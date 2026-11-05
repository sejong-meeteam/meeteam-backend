package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.net.URI;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "integration.github.account")
public class GithubAccountOAuthProperties {
    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String stateSecret;
    private String frontOrigin;
    private String callbackUri;

    public void requireEnabled() {
        try {
            if (!enabled || blank(clientId) || blank(clientSecret) || blank(stateSecret)
                    || Base64.getDecoder().decode(stateSecret).length < 32) {
                throw new IllegalArgumentException();
            }
            URI origin = validatedOrigin(frontOrigin);
            URI callback = URI.create(callbackUri);
            if (!origin.getScheme().equalsIgnoreCase(callback.getScheme())
                    || !origin.getHost().equalsIgnoreCase(callback.getHost())
                    || effectivePort(origin) != effectivePort(callback)
                    || callback.getRawUserInfo() != null || callback.getRawQuery() != null
                    || callback.getRawFragment() != null
                    || !"/api/v1/github/oauth/callback".equals(callback.getRawPath())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ignored) {
            throw new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED, "GitHub 계정 연결이 설정되지 않았습니다.");
        }
    }

    static URI validatedOrigin(String value) {
        URI uri = URI.create(value);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        boolean local = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!secure && !local) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (!uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                || uri.getPort() > 65535 || uri.getPort() == 0) {
            throw new IllegalArgumentException();
        }
        return uri;
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getStateSecret() { return stateSecret; }
    public void setStateSecret(String stateSecret) { this.stateSecret = stateSecret; }
    public String getFrontOrigin() { return frontOrigin; }
    public void setFrontOrigin(String frontOrigin) { this.frontOrigin = frontOrigin; }
    public String getCallbackUri() { return callbackUri; }
    public void setCallbackUri(String callbackUri) { this.callbackUri = callbackUri; }
}
