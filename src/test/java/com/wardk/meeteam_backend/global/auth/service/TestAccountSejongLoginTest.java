package com.wardk.meeteam_backend.global.auth.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongLoginCommand;
import com.wardk.meeteam_backend.global.auth.service.dto.TokenExchangeResult;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestAccountSejongLoginTest {
    @Mock MemberRepository memberRepository;
    @Mock SejongPortalClient sejongPortalClient;
    @Mock JwtUtil jwtUtil;
    @Mock ObjectProvider<TestAccountService> testAccountService;
    @Mock TestAccountService testAccount;
    @InjectMocks AuthService auth;

    @Test
    void numericTestAccountUsesVerifiedPasswordWithoutContactingPortal() {
        when(testAccountService.getIfAvailable()).thenReturn(testAccount);
        when(testAccount.login("99990001", "unit-test-only"))
                .thenReturn(new TokenExchangeResult("unit-test-access-placeholder", "unit-test-refresh-placeholder"));
        var result = auth.sejongLogin(new SejongLoginCommand("99990001", "unit-test-only"));
        assertFalse(result.isNewMember());
        assertNull(result.getCode());
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        verify(testAccount).login("99990001", "unit-test-only");
        verifyNoInteractions(sejongPortalClient, memberRepository);
    }

    @Test
    void wrongTestPasswordIsRejectedAndDoesNotFallBackToPortal() {
        when(testAccountService.getIfAvailable()).thenReturn(testAccount);
        when(testAccount.login("99990001", "wrong"))
                .thenThrow(new CustomException(ErrorCode.BAD_CREDENTIALS));
        assertThrows(CustomException.class, () -> auth.sejongLogin(new SejongLoginCommand("99990001", "wrong")));
        verifyNoInteractions(sejongPortalClient, memberRepository, jwtUtil);
    }

    @Test
    void realStudentsStillRequirePortalAuthenticationWhenTestLoginIsEnabled() {
        when(testAccountService.getIfAvailable()).thenReturn(testAccount);
        when(sejongPortalClient.authenticate("21000001", "unit-test-only")).thenReturn(true);
        Member member = Member.createForTest("unit-test@example.invalid", "Test");
        when(memberRepository.findByStudentIdAndDeletedAtIsNull("21000001")).thenReturn(Optional.of(member));
        auth.sejongLogin(new SejongLoginCommand("21000001", "unit-test-only"));
        verify(sejongPortalClient).authenticate("21000001", "unit-test-only");
        verifyNoInteractions(testAccount);
    }

    @Test
    void numericIdDoesNotBypassPortalWhenTestFeatureIsAbsent() {
        when(testAccountService.getIfAvailable()).thenReturn(null);
        when(sejongPortalClient.authenticate("99990001", "unit-test-only")).thenReturn(true);
        Member member = Member.createForTest("unit-test@example.invalid", "Test");
        when(memberRepository.findByStudentIdAndDeletedAtIsNull("99990001")).thenReturn(Optional.of(member));
        auth.sejongLogin(new SejongLoginCommand("99990001", "unit-test-only"));
        verify(sejongPortalClient).authenticate("99990001", "unit-test-only");
        verifyNoInteractions(testAccount);
    }
}
