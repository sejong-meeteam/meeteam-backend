package com.wardk.meeteam_backend.adminloginunit;

import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.github.account.MemberGithubAccountRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.repository.SejongCodeRepository;
import com.wardk.meeteam_backend.global.auth.service.*;
import com.wardk.meeteam_backend.global.auth.service.dto.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminLoginRoutingTest {
    @Mock MemberRepository members;
    @Mock JwtUtil jwt;
    @Mock SejongPortalClient portal;
    @Mock SejongCodeRepository codes;
    @Mock MemberGithubAccountRepository githubAccounts;
    @Mock ObjectProvider<TestAccountService> testAccounts;
    @Mock AdminAccountService admins;
    @InjectMocks AuthService auth;
    @BeforeEach void wireOptionalAdmin() { ReflectionTestUtils.setField(auth, "adminAccountService", admins); }

    @Test void administratorUsesExistingLoginResultAndNeverCallsStudentPortal() {
        when(admins.matchesUsername("99123456")).thenReturn(true);
        when(admins.login("99123456", "synthetic-input")).thenReturn(new TokenExchangeResult("synthetic-access", "synthetic-refresh"));
        var result = auth.sejongLogin(new SejongLoginCommand("99123456", "synthetic-input"));
        assertThat(result.isNewMember()).isFalse();
        assertThat(result.getAccessToken()).isEqualTo("synthetic-access");
        assertThat(result.getRefreshToken()).isEqualTo("synthetic-refresh");
        assertThat(result.getCode()).isNull();
        verifyNoInteractions(portal, members, codes, jwt, testAccounts);
    }
    @Test void badAdministratorPasswordNeverFallsBackToPortalOrRegistration() {
        when(admins.matchesUsername("99123456")).thenReturn(true);
        when(admins.login(anyString(), anyString())).thenThrow(new CustomException(ErrorCode.BAD_CREDENTIALS));
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand("99123456", "wrong")))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BAD_CREDENTIALS));
        verifyNoInteractions(portal, members, codes, jwt, testAccounts);
    }
    @Test void regularStudentStillRequiresSchoolAuthentication() {
        when(members.findByStudentIdAndDeletedAtIsNull("20261234")).thenReturn(Optional.empty());
        when(codes.saveRegisterInfo(any())).thenReturn("synthetic-registration");
        when(portal.authenticateAndFetchProfile("20261234", "synthetic-school-input"))
                .thenReturn(new com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile("20261234", "테스트 학생", "테스트학과"));
        var result = auth.sejongLogin(new SejongLoginCommand("20261234", "synthetic-school-input"));
        assertThat(result.isNewMember()).isTrue();
        verify(portal).authenticateAndFetchProfile("20261234", "synthetic-school-input");
        verify(admins, never()).login(any(), any());
        verifyNoInteractions(jwt);
    }
    @Test void disabledLocalAdminDoesNotBypassPortal() {
        ReflectionTestUtils.setField(auth, "adminAccountService", null);
        when(members.findByStudentIdAndDeletedAtIsNull("99123456")).thenReturn(Optional.empty());
        when(portal.authenticateAndFetchProfile("99123456", "synthetic-input"))
                .thenThrow(new CustomException(ErrorCode.SEJONG_LOGIN_FAILED));
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand("99123456", "synthetic-input")))
                .isInstanceOf(CustomException.class);
        verify(portal).authenticateAndFetchProfile("99123456", "synthetic-input");
        verifyNoInteractions(admins);
    }
    @Test void existingNumericTestAccountRemainsSeparate() {
        var test = mock(TestAccountService.class);
        when(testAccounts.getIfAvailable()).thenReturn(test);
        when(test.login(TestAccountService.USERNAME, "synthetic-input")).thenReturn(new TokenExchangeResult("test-access", "test-refresh"));
        var result = auth.sejongLogin(new SejongLoginCommand(TestAccountService.USERNAME, "synthetic-input"));
        assertThat(result.isNewMember()).isFalse();
        assertThat(result.getAccessToken()).isEqualTo("test-access");
        verify(admins, never()).login(any(), any());
        verifyNoInteractions(portal, members, codes, jwt);
    }
    @Test void registrationCodeCannotCreateStudentAtReservedAdminLogin() {
        when(admins.matchesUsername("99123456")).thenReturn(true);
        when(codes.consumeRegisterInfo("synthetic-code")).thenReturn(Optional.of(new SejongRegisterInfo("99123456")));
        var command = new SejongRegisterCommand("synthetic-code", "Synthetic", null, Gender.MALE, List.of(), null, null);
        assertThatThrownBy(() -> auth.sejongRegister(command, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEJONG_CODE));
        verifyNoInteractions(members, portal, jwt);
    }
    @Test void localAdminCannotAccidentallyWithdrawThroughStudentAccountPage() {
        var member = Member.createForLocalAdmin(AdminAccountService.EMAIL, "synthetic-hash");
        when(members.findByIdForUpdate(41L)).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> auth.withdraw(41L)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_INVALID_STATE));
        assertThat(member.isDeleted()).isFalse();
        verifyNoInteractions(githubAccounts);
    }
    @Test void localAdminHardDeletionIsRejectedBeforeAnyCleanup() {
        var member = Member.createForLocalAdmin(AdminAccountService.EMAIL, "synthetic-hash");
        when(members.findById(41L)).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> auth.deleteMember(41L)).isInstanceOf(CustomException.class);
        verify(members, never()).delete(any());
        verifyNoInteractions(githubAccounts);
    }
}
