package com.wardk.meeteam_backend.github.account;

import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubOAuthStateService.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.GithubAccountOAuthClient.Identity;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GithubAccountOAuthServiceTest {
    @Mock GithubAccountService accounts;
    @Mock GithubAccountOAuthProperties properties;
    @Mock GithubOAuthStateService states;
    @Mock GithubAccountOAuthClient client;
    @InjectMocks GithubAccountOAuthService service;
    private final Validated valid = new Validated(7, 2, 3, "/settings?tab=profile", "v".repeat(43));
    private final Identity identity = new Identity(123, "unit-dev", "");
    @BeforeEach void origin() { lenient().when(properties.getFrontOrigin()).thenReturn("https://front.example.invalid"); }
    private void validated() { when(states.consume("signed-state")).thenReturn(valid); }
    @Test void authorizeUsesCurrentMemberRevisionsAndPkce() {
        when(accounts.context(7)).thenReturn(new GithubAccountService.LinkContext(7, 2, 3));
        when(states.issue(7, 2, 3, "/settings")).thenReturn(new Issued("signed-state", "challenge"));
        when(client.authorizeUrl("signed-state", "challenge")).thenReturn("https://github.com/authorize");
        assertThat(service.authorize(7, "/settings")).isEqualTo("https://github.com/authorize");
        verify(states).issue(7, 2, 3, "/settings");
    }
    @Test void invalidRedirectNeverIssuesState() {
        assertThatThrownBy(() -> service.authorize(7, "//evil.invalid")).isInstanceOf(CustomException.class);
        verifyNoInteractions(accounts, states, client);
    }
    @Test void successfulCallbackExchangesOnceAndConnectsVerifiedMember() {
        validated(); when(client.identify("code", valid.codeVerifier())).thenReturn(identity);
        assertThat(service.callback("code", "signed-state", null, 7L).toString())
                .isEqualTo("https://front.example.invalid/settings?tab=profile&github=connected");
        var order = inOrder(states, client, accounts);
        order.verify(states).consume("signed-state");
        order.verify(client).identify("code", valid.codeVerifier());
        order.verify(accounts).connect(valid, identity);
    }
    @Test void callbackWithoutMeeTeamSessionCannotLink() {
        validated();
        assertThat(service.callback("code", "signed-state", null, null).getQuery()).endsWith("github=failed");
        verifyNoInteractions(client, accounts);
    }
    @Test void callbackInDifferentMembersBrowserCannotLink() {
        validated();
        assertThat(service.callback("code", "signed-state", null, 8L).getQuery()).endsWith("github=failed");
        verifyNoInteractions(client, accounts);
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "access_denied"})
    void providerDeniedAlwaysConsumesStateAndDoesNotExchange(String error) {
        validated();
        assertThat(service.callback("code", "signed-state", error, 7L).getQuery()).endsWith("github=failed");
        verify(states).consume("signed-state"); verifyNoInteractions(client, accounts);
    }
    @Test void missingCodeDoesNotExchange() {
        validated(); assertThat(service.callback(null, "signed-state", null, 7L).getQuery()).endsWith("github=failed");
        verifyNoInteractions(client, accounts);
    }
    @Test void invalidOrReplayedStateUsesSafeDefaultPath() {
        when(states.consume("signed-state")).thenThrow(new CustomException(ErrorCode.GITHUB_INVALID_STATE));
        assertThat(service.callback("code", "signed-state", null, 7L).toString())
                .isEqualTo("https://front.example.invalid/settings?github=failed");
        verifyNoInteractions(client, accounts);
    }
    @Test void githubFailureNeverMutatesAccountAndDoesNotReflectError() {
        validated(); when(client.identify(anyString(), anyString())).thenThrow(new IllegalStateException("sensitive-upstream-payload"));
        assertThat(service.callback("code", "signed-state", null, 7L).toString()).endsWith("github=failed")
                .doesNotContain("sensitive-upstream-payload"); verifyNoInteractions(accounts);
    }
    @Test void uniquenessRaceAndTransactionRollbackReturnFailedNotConnected() {
        validated(); when(client.identify(anyString(), anyString())).thenReturn(identity);
        doThrow(new DataIntegrityViolationException("private-sql-details")).when(accounts).connect(valid, identity);
        assertThat(service.callback("code", "signed-state", null, 7L).toString()).endsWith("github=failed")
                .doesNotContain("private-sql-details");
    }
    @Test void disabledConfigurationNeverIssuesStateOrCallsGithub() {
        doThrow(new CustomException(ErrorCode.GITHUB_NOT_CONFIGURED)).when(properties).requireEnabled();
        assertThatThrownBy(() -> service.authorize(7, "/settings")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.callback("code", "signed-state", null, 7L)).isInstanceOf(CustomException.class);
        verifyNoInteractions(accounts, states, client);
    }
}
