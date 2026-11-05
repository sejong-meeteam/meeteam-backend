package com.wardk.meeteam_backend.adminloginunit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.member.service.MemberProfileServiceImpl;
import com.wardk.meeteam_backend.global.auth.service.*;
import com.wardk.meeteam_backend.global.logging.MethodLoggingAspect;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccountIsolationTest {
    @Test void blankBootstrapPasswordCannotCreateAnUnusableOperator() {
        assertThatThrownBy(() -> new AdminAccountService(mock(MemberRepository.class), new BCryptPasswordEncoder(4),
                mock(JwtUtil.class), "99123456", " ".repeat(24))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void factoryCreatesOperatorOnlyAndDoesNotChangeStudentOrTestFactories() {
        var admin = Member.createForLocalAdmin(AdminAccountService.EMAIL, "synthetic-hash");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.isLocalAdminAccount()).isTrue();
        assertThat(admin.getStudentId()).isNull();
        assertThat(admin.getIsParticipating()).isFalse();
        assertThat(Member.createForTest(TestAccountService.EMAIL, "Test").getRole()).isEqualTo(UserRole.USER);
        assertThat(Member.createForTest(TestAccountService.EMAIL, "Test").isLocalAdminAccount()).isFalse();
    }
    @Test void publicMemberDetailHidesOperatorButOwnProfileStillExposesRole() {
        var members = mock(MemberRepository.class);
        var admin = Member.createForLocalAdmin(AdminAccountService.EMAIL, "synthetic-hash");
        when(members.findById(41L)).thenReturn(Optional.of(admin));
        var profiles = new MemberProfileServiceImpl(members, null, null, null, null, null);
        assertThatThrownBy(() -> profiles.getMemberDetail(41L)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
        assertThat(profiles.profile(41L).getRole()).isEqualTo(UserRole.ADMIN);
    }
    @Test void authenticationAspectNeverLogsRawPasswordArgumentsOrTokensEvenOnFailure() {
        String syntheticPassword = "synthetic-login-only-".repeat(2);
        var members = mock(MemberRepository.class);
        var jwt = mock(JwtUtil.class);
        var encoder = new BCryptPasswordEncoder(4);
        var member = Member.createForLocalAdmin(AdminAccountService.EMAIL, encoder.encode(syntheticPassword));
        when(members.findByEmailAndDeletedAtIsNull(AdminAccountService.EMAIL)).thenReturn(Optional.of(member));
        when(jwt.createAccessToken(member)).thenReturn("synthetic-access-token-only");
        when(jwt.createRefreshToken(member)).thenReturn("synthetic-refresh-token-only");
        var factory = new AspectJProxyFactory(new AdminAccountService(members, encoder, jwt, "99123456", syntheticPassword));
        factory.setProxyTargetClass(true);
        factory.addAspect(new MethodLoggingAspect());
        var proxy = (AdminAccountService) factory.getProxy();
        var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel(); root.setLevel(Level.INFO);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); root.addAppender(logs);
        try {
            assertThat(proxy.login("99123456", syntheticPassword).getAccessToken()).isEqualTo("synthetic-access-token-only");
            assertThatThrownBy(() -> proxy.login("99123456", "synthetic-wrong-input")).isInstanceOf(CustomException.class);
            assertThat(logs.list).isEmpty();
        } finally { root.detachAppender(logs); root.setLevel(previous); logs.stop(); }
    }
}
