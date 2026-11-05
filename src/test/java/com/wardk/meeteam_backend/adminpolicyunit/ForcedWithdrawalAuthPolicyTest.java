package com.wardk.meeteam_backend.adminpolicyunit;

import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.repository.SejongCodeRepository;
import com.wardk.meeteam_backend.global.auth.service.*;
import com.wardk.meeteam_backend.global.auth.service.dto.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForcedWithdrawalAuthPolicyTest {
    @Mock MemberRepository members;
    @Mock JwtUtil jwt;
    @Mock SejongPortalClient portal;
    @Mock SejongCodeRepository codes;
    @Mock ObjectProvider<TestAccountService> testAccounts;
    @InjectMocks AuthService auth;

    @Test void forcedWithdrawalCannotObtainTokensOrFreshRegistrationCode() {
        when(members.existsByStudentIdAndAdminWithdrawnTrue("20261234")).thenReturn(true);
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand("20261234", "unit-password")))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_WITHDRAWN));
        verify(portal).authenticate("20261234", "unit-password");
        verifyNoInteractions(jwt, codes);
    }
    @Test void registrationCodeIssuedBeforeForcedWithdrawalCannotBeRedeemed() {
        when(codes.consumeRegisterInfo("unit-registration-code")).thenReturn(Optional.of(new SejongRegisterInfo("20261234")));
        when(members.existsByStudentIdAndAdminWithdrawnTrue("20261234")).thenReturn(true);
        var command = new SejongRegisterCommand("unit-registration-code", "회원", null, Gender.MALE, List.of(), null, null);
        assertThatThrownBy(() -> auth.sejongRegister(command, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_WITHDRAWN));
        verify(members, never()).save(any());
        verifyNoInteractions(jwt);
    }
    @Test void ordinaryVoluntaryWithdrawalRejoinFlowRemainsUnchanged() {
        when(members.findByStudentIdAndDeletedAtIsNull("20261234")).thenReturn(Optional.empty());
        when(codes.saveRegisterInfo(any())).thenReturn("unit-registration-code");
        var response = auth.sejongLogin(new SejongLoginCommand("20261234", "unit-password"));
        assertThat(response.isNewMember()).isTrue();
        verify(codes).saveRegisterInfo(any());
        verifyNoInteractions(jwt);
    }

    @Test void currentLockedIdentityBlocksSignupEvenIfEarlierSnapshotWouldMissMarker() {
        when(codes.consumeRegisterInfo("unit-registration-code")).thenReturn(Optional.of(new SejongRegisterInfo("20261234")));
        var removed = com.wardk.meeteam_backend.domain.member.entity.Member.createForTest("unit@example.invalid", "탈퇴 회원");
        removed.forceWithdraw();
        when(members.findAllByStudentIdForUpdate("20261234")).thenReturn(List.of(removed));
        var command = new SejongRegisterCommand("unit-registration-code", "회원", null, Gender.MALE, List.of(), null, null);
        assertThatThrownBy(() -> auth.sejongRegister(command, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_WITHDRAWN));
        verify(members, never()).existsByStudentIdAndAdminWithdrawnTrue(anyString());
        verify(members, never()).save(any());
        verifyNoInteractions(jwt);
    }
}
