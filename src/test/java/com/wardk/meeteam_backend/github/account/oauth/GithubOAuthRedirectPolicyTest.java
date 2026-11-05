package com.wardk.meeteam_backend.github.account.oauth;

import com.wardk.meeteam_backend.domain.github.account.oauth.GithubOAuthRedirectPolicy;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class GithubOAuthRedirectPolicyTest {
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"https://evil.example", "//evil.example", "/\\evil.example", "/%2Fevil", "/%5cevil", "/%252fevil",
            "/settings#fragment", "/settings\r\nLocation: evil", "/settings?x=%0D%0A", "/%00", "/%7f", "/%zz", "relative"})
    void rejectsUnsafeRedirects(String input) {
        assertThatThrownBy(() -> GithubOAuthRedirectPolicy.validatePath(input))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GITHUB_INVALID_REQUEST));
    }

    @Test void enforcesLengthAndAcceptsInternalPaths() {
        String maximum = "/" + "a".repeat(1023);
        assertThat(GithubOAuthRedirectPolicy.validatePath(maximum)).isEqualTo(maximum);
        assertThatThrownBy(() -> GithubOAuthRedirectPolicy.validatePath(maximum + "a")).isInstanceOf(CustomException.class);
        assertThat(GithubOAuthRedirectPolicy.validatePath("/settings?tab=profile")).isEqualTo("/settings?tab=profile");
    }

    @Test void boundsUtf8SizeOfSignedRedirectPath() {
        assertThat(GithubOAuthRedirectPolicy.validatePath("/" + "가".repeat(341))).isNotEmpty();
        assertThatThrownBy(() -> GithubOAuthRedirectPolicy.validatePath("/" + "가".repeat(342)))
                .isInstanceOf(CustomException.class);
    }

    @Test void replacesAllGithubValuesAndPreservesSafeQuerySemantics() {
        URI uri = GithubOAuthRedirectPolicy.resultUri("https://meeteam.example/",
                "/settings?tab=profile&github=old&%67ithub=another&q=hello%20world&tag=a%26b&plus=a%2Bb", true);
        assertThat(uri.toASCIIString()).isEqualTo("https://meeteam.example/settings?tab=profile&q=hello%20world&tag=a%26b&plus=a%2Bb&github=connected");
    }

    @Test void failureStatusAndOriginCannotBeOverridden() {
        assertThat(GithubOAuthRedirectPolicy.resultUri("http://localhost:3000", "/", false).toString())
                .isEqualTo("http://localhost:3000/?github=failed");
        assertThatThrownBy(() -> GithubOAuthRedirectPolicy.resultUri("https://user@evil.example", "/", true))
                .isInstanceOf(CustomException.class);
    }
}
