package com.wardk.meeteam_backend.github.account;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wardk.meeteam_backend.domain.github.account.*;
import com.wardk.meeteam_backend.domain.github.account.oauth.*;
import com.wardk.meeteam_backend.global.logging.MethodLoggingAspect;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.stereotype.Service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubAccountLoggingTest {
    @Service public static class OrdinaryService { public String echo() { return "ordinary"; } }
    @Test void aspectStillLogsOrdinaryServiceButNeverLogsOAuthArgumentsOrReturnValues() {
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); root.addAppender(appender);
        Level old = root.getLevel(); root.setLevel(Level.INFO);
        try {
            var ordinaryFactory = new AspectJProxyFactory(new OrdinaryService());
            ordinaryFactory.addAspect(new MethodLoggingAspect());
            ((OrdinaryService) ordinaryFactory.getProxy()).echo();
            assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("ordinary"));
            appender.list.clear();
            var accounts = mock(GithubAccountService.class);
            var properties = mock(GithubAccountOAuthProperties.class);
            var states = mock(GithubOAuthStateService.class);
            var client = mock(GithubAccountOAuthClient.class);
            when(accounts.context(7)).thenReturn(new GithubAccountService.LinkContext(7, 0, 0));
            when(states.issue(7, 0, 0, "/settings")).thenReturn(new GithubOAuthStateService.Issued("sensitive-state", "challenge"));
            when(client.authorizeUrl("sensitive-state", "challenge")).thenReturn("https://github.com/?state=sensitive-state");
            var factory = new AspectJProxyFactory(new GithubAccountOAuthService(accounts, properties, states, client));
            factory.addAspect(new MethodLoggingAspect());
            var proxy = (GithubAccountOAuthService) factory.getProxy();
            assertThat(proxy.authorize(7, "/settings")).contains("sensitive-state");
            assertThat(appender.list).isEmpty();
        } finally { root.detachAppender(appender); root.setLevel(old); appender.stop(); }
    }
}
