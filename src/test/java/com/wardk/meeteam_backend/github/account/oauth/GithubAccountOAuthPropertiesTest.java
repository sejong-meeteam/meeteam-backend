package com.wardk.meeteam_backend.github.account.oauth;

import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthProperties;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class GithubAccountOAuthPropertiesTest {
    static GithubAccountOAuthProperties configured() {
        GithubAccountOAuthProperties p = new GithubAccountOAuthProperties();
        p.setEnabled(true);
        p.setClientId("client-id");
        p.setClientSecret("private-client-secret");
        p.setStateSecret(Base64.getEncoder().encodeToString(new byte[32]));
        p.setFrontOrigin("https://meeteam.example");
        p.setCallbackUri("https://meeteam.example/api/v1/github/oauth/callback");
        return p;
    }

    @Test void disabledByDefaultAndErrorsDoNotDiscloseSecrets() {
        assertThat(new GithubAccountOAuthProperties().isEnabled()).isFalse();
        assertNotConfigured(new GithubAccountOAuthProperties());
        GithubAccountOAuthProperties p = configured();
        p.setEnabled(false);
        assertNotConfigured(p);
        assertThat(p.toString()).doesNotContain(p.getClientSecret(), p.getStateSecret());
    }

    @Test void requiresEveryCredentialAndStrongBase64Secret() {
        for (String value : new String[] {null, "", " ", "not-base64", Base64.getEncoder().encodeToString(new byte[31])}) {
            GithubAccountOAuthProperties p = configured();
            p.setStateSecret(value);
            assertNotConfigured(p);
        }
        GithubAccountOAuthProperties p = configured();
        p.setClientId(null);
        assertNotConfigured(p);
        p = configured();
        p.setClientSecret(" ");
        assertNotConfigured(p);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://meeteam.example", "https://meeteam.example/", "http://localhost:3000", "http://127.0.0.1:3000"})
    void acceptsSecureOriginOrExplicitLocalDevelopment(String origin) {
        GithubAccountOAuthProperties p = configured();
        p.setFrontOrigin(origin);
        p.setCallbackUri(origin.replaceAll("/$", "") + "/api/v1/github/oauth/callback");
        assertThatCode(p::requireEnabled).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://meeteam.example", "https://meeteam.example/settings", "https://user@meeteam.example",
            "https://meeteam.example?query", "https://meeteam.example#fragment", "https://meeteam.example:0", "https://meeteam.example:65536", "//meeteam.example"})
    void rejectsInsecureOrNonOriginConfiguration(String origin) {
        GithubAccountOAuthProperties p = configured();
        p.setFrontOrigin(origin);
        assertNotConfigured(p);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://meeteam.example/api/v1/github/oauth/callback", "https://evil.example/api/v1/github/oauth/callback",
            "https://meeteam.example:444/api/v1/github/oauth/callback", "https://meeteam.example/callback",
            "https://meeteam.example/api/v1/github/oauth/callback?x=1", "https://meeteam.example/api/v1/github/oauth/callback#x",
            "https://user@meeteam.example/api/v1/github/oauth/callback", "https://meeteam.example/api/v1/github/oauth/%63allback"})
    void callbackMustBeExactAndSameOrigin(String callback) {
        GithubAccountOAuthProperties p = configured();
        p.setCallbackUri(callback);
        assertNotConfigured(p);
    }

    private static void assertNotConfigured(GithubAccountOAuthProperties p) {
        assertThatThrownBy(p::requireEnabled).isInstanceOfSatisfying(CustomException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GITHUB_NOT_CONFIGURED);
            assertThat(ex.getCause()).isNull();
            assertThat(ex.getMessage()).doesNotContain("private-client-secret");
        });
    }
}
