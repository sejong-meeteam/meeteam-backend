package com.wardk.meeteam_backend.domain.github.account.oauth;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

public final class GithubOAuthRedirectPolicy {
    private GithubOAuthRedirectPolicy() { }

    public static String validatePath(String path) {
        try {
            if (path == null || path.length() > 1024 || path.getBytes(StandardCharsets.UTF_8).length > 1024
                    || !path.startsWith("/") || path.startsWith("//")
                    || path.indexOf('\\') >= 0 || path.indexOf('#') >= 0
                    || path.chars().anyMatch(c -> Character.isISOControl(c))) {
                throw new IllegalArgumentException();
            }
            String lower = path.toLowerCase(Locale.ROOT);
            // Reject nested encoding as well as separators and controls at any URI position.
            if (lower.matches(".*%(?:2f|5c|25|0[0-9a-f]|1[0-9a-f]|7f).*")) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(path);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                    || uri.getPath().chars().anyMatch(Character::isISOControl)
                    || (uri.getQuery() != null && uri.getQuery().chars().anyMatch(Character::isISOControl))) {
                throw new IllegalArgumentException();
            }
            return path;
        } catch (RuntimeException ignored) {
            throw invalid();
        }
    }

    public static URI resultUri(String frontOrigin, String redirectPath, boolean connected) {
        validatePath(redirectPath);
        try {
            URI origin = GithubAccountOAuthProperties.validatedOrigin(frontOrigin);
            URI redirect = URI.create(redirectPath);
            UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                    .scheme(origin.getScheme()).host(origin.getHost()).port(origin.getPort())
                    .path(redirect.getPath());
            Map<String, Object> variables = new LinkedHashMap<>();
            int index = 0;
            if (redirect.getRawQuery() != null) {
                for (String parameter : redirect.getRawQuery().split("&")) {
                    if (parameter.isEmpty()) continue;
                    String[] pair = parameter.split("=", 2);
                    String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                    if (!"github".equals(name)) {
                        String key = "key" + index;
                        String value = "value" + index++;
                        variables.put(key, name);
                        if (pair.length == 2) {
                            variables.put(value, URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                            builder.queryParam("{" + key + "}", "{" + value + "}");
                        } else {
                            builder.queryParam("{" + key + "}");
                        }
                    }
                }
            }
            return builder.queryParam("github", connected ? "connected" : "failed").encode().buildAndExpand(variables).toUri();
        } catch (RuntimeException ignored) {
            throw invalid();
        }
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.GITHUB_INVALID_REQUEST, "GitHub 연결 요청이 올바르지 않습니다.");
    }
}
