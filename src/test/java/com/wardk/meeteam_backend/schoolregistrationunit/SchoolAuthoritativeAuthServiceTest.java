package com.wardk.meeteam_backend.schoolregistrationunit;

import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile;
import com.wardk.meeteam_backend.global.auth.repository.SejongCodeRepository;
import com.wardk.meeteam_backend.global.auth.service.*;
import com.wardk.meeteam_backend.global.auth.service.dto.*;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic identities only. No context, network, database, Redis, or credential store. */
@ExtendWith(MockitoExtension.class)
class SchoolAuthoritativeAuthServiceTest {
    private static final String ID = "20261234";
    private static final String CODE = "synthetic-registration-code";
    private static final String PASSWORD = "synthetic-input";
    @Mock MemberRepository members;
    @Mock SejongCodeRepository codes;
    @Mock SejongPortalClient portal;
    @Mock JwtUtil jwt;
    @Mock BCryptPasswordEncoder encoder;
    @Mock S3FileService s3;
    @Mock ObjectProvider<TestAccountService> testAccounts;
    @InjectMocks AuthService auth;

    static SejongRegisterCommand command() {
        return new SejongRegisterCommand(CODE, "Client Spoof", LocalDate.of(2000, 1, 2),
                Gender.MALE, List.of(), "https://github.com/synthetic", null);
    }
    private MockMultipartFile image() {
        return new MockMultipartFile("file", "synthetic.png", "image/png", new byte[]{1, 2});
    }
    private void assertNoRegistrationSideEffects() {
        verifyNoInteractions(s3, encoder, jwt);
        verify(members, never()).save(any());
    }

    @Test void registrationUsesOnlySchoolNameAndDepartmentDespiteSpoofedCommand() {
        when(codes.consumeRegisterInfo(CODE)).thenReturn(Optional.of(new SejongRegisterInfo(ID, "School Name", "Computer Science")));
        var file = image();
        when(s3.uploadFile(file, "images")).thenReturn("images/synthetic.png");
        when(encoder.encode(anyString())).thenReturn("synthetic-hash");
        when(jwt.createAccessToken(any())).thenReturn("synthetic-access");
        when(jwt.createRefreshToken(any())).thenReturn("synthetic-refresh");

        var result = auth.sejongRegister(command(), file);

        var saved = ArgumentCaptor.forClass(Member.class);
        verify(members).save(saved.capture());
        Member member = saved.getValue();
        assertThat(member.getRealName()).isEqualTo("School Name").isNotEqualTo(command().name());
        assertThat(member.getDepartment()).isEqualTo("Computer Science");
        assertThat(member.isSchoolProfileVerified()).isTrue();
        assertThat(member.getStudentId()).isEqualTo(ID);
        assertThat(member.getEmail()).isEqualTo(ID + "@sju.ac.kr");
        assertThat(member.getBirth()).isEqualTo(command().birthDate());
        assertThat(member.getStoreFileName()).isEqualTo("images/synthetic.png");
        assertThat(result.getAccessToken()).isEqualTo("synthetic-access");
        verify(jwt).createAccessToken(member);
        verify(jwt).createRefreshToken(member);
        verify(codes).consumeRegisterInfo(CODE);
        verifyNoInteractions(portal);
    }

    @Test void expiredOrMissingCodeCannotUploadSaveOrIssueTokens() {
        when(codes.consumeRegisterInfo(CODE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.sejongRegister(command(), image()))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEJONG_CODE));
        verifyNoInteractions(members);
        assertNoRegistrationSideEffects();
    }

    static Stream<SejongRegisterInfo> invalidStoredProfiles() {
        return Stream.of(new SejongRegisterInfo(ID), new SejongRegisterInfo(),
                new SejongRegisterInfo(ID, null, "Department"),
                new SejongRegisterInfo(ID, " ", "Department"),
                new SejongRegisterInfo(ID, "School Name", null),
                new SejongRegisterInfo(ID, "School Name", " "),
                new SejongRegisterInfo("invalid-id", "School Name", "Department"),
                new SejongRegisterInfo(ID, "n".repeat(101), "Department"),
                new SejongRegisterInfo(ID, "School Name", "d".repeat(201)),
                new SejongRegisterInfo(ID, "Bad\nName", "Department"));
    }
    @ParameterizedTest @MethodSource("invalidStoredProfiles")
    void legacyMissingOrMalformedProfileIsRejectedBeforeSideEffects(SejongRegisterInfo info) {
        when(codes.consumeRegisterInfo(CODE)).thenReturn(Optional.of(info));
        assertThatThrownBy(() -> auth.sejongRegister(command(), image()))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEJONG_CODE));
        assertNoRegistrationSideEffects();
    }

    @Test void withdrawalGuardWinsOverLegacyProfileValidation() {
        when(codes.consumeRegisterInfo(CODE)).thenReturn(Optional.of(new SejongRegisterInfo(ID)));
        when(members.existsByStudentIdAndAdminWithdrawnTrue(ID)).thenReturn(true);
        assertThatThrownBy(() -> auth.sejongRegister(command(), image()))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_WITHDRAWN));
        assertNoRegistrationSideEffects();
    }

    @Test void duplicateGuardWinsOverLegacyProfileValidation() {
        when(codes.consumeRegisterInfo(CODE)).thenReturn(Optional.of(new SejongRegisterInfo(ID)));
        when(members.existsByStudentIdAndDeletedAtIsNull(ID)).thenReturn(true);
        assertThatThrownBy(() -> auth.sejongRegister(command(), image()))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEJONG_STUDENT_ID_ALREADY_EXISTS));
        assertNoRegistrationSideEffects();
    }

    @Test void newLoginFetchesProfileOnceAndBindsActualIdentityToCode() {
        var profile = new SejongPortalProfile(ID, "School Name", "Computer Science");
        when(portal.authenticateAndFetchProfile(ID, PASSWORD)).thenReturn(profile);
        when(codes.saveRegisterInfo(any())).thenReturn(CODE);
        var result = auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD));
        assertThat(result.isNewMember()).isTrue();
        assertThat(result.getVerifiedProfile()).isSameAs(profile);
        assertThat(result.getCode()).isEqualTo(CODE);
        assertThat(result.getAccessToken()).isNull();
        assertThat(result.getRefreshToken()).isNull();
        var info = ArgumentCaptor.forClass(SejongRegisterInfo.class);
        verify(codes).saveRegisterInfo(info.capture());
        assertThat(info.getValue().requireVerifiedProfile()).isEqualTo(profile);
        verify(portal, times(1)).authenticateAndFetchProfile(ID, PASSWORD);
        verify(portal, never()).authenticate(anyString(), anyString());
        verifyNoInteractions(jwt);
    }

    @Test void profileFetchFailureCannotIssueRegistrationCodeOrTokens() {
        when(portal.authenticateAndFetchProfile(ID, PASSWORD)).thenThrow(new CustomException(ErrorCode.SEJONG_PORTAL_ERROR));
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEJONG_PORTAL_ERROR));
        verifyNoInteractions(codes, jwt);
    }

    @Test void mismatchedAuthenticatedStudentIdCannotIssueCodeOrTokens() {
        when(portal.authenticateAndFetchProfile(ID, PASSWORD)).thenReturn(new SejongPortalProfile("20269999", "Other Name", "Department"));
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEJONG_PORTAL_ERROR));
        verifyNoInteractions(codes, jwt);
    }

    @Test void missingPortalProfileFailsClosed() {
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SEJONG_PORTAL_ERROR));
        verifyNoInteractions(codes, jwt);
    }

    @Test void existingLegacyMemberOnlyAuthenticatesAndKeepsExistingIdentity() {
        Member member = Member.createSejongMember(ID, command(), "synthetic-hash", null);
        when(members.findByStudentIdAndDeletedAtIsNull(ID)).thenReturn(Optional.of(member));
        var result = auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD));
        assertThat(result.isNewMember()).isFalse();
        assertThat(result.getVerifiedProfile()).isNull();
        assertThat(result.getCode()).isNull();
        assertThat(member.getRealName()).isEqualTo("Client Spoof");
        assertThat(member.getDepartment()).isNull();
        assertThat(member.isSchoolProfileVerified()).isFalse();
        verify(portal).authenticate(ID, PASSWORD);
        verify(portal, never()).authenticateAndFetchProfile(anyString(), anyString());
        verifyNoInteractions(codes);
        verify(members, never()).save(any());
    }

    @Test void forcedWithdrawalAuthenticatesOnlyThenRejectsWithoutProfileFetch() {
        when(members.existsByStudentIdAndAdminWithdrawnTrue(ID)).thenReturn(true);
        assertThatThrownBy(() -> auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEMBER_WITHDRAWN));
        verify(portal).authenticate(ID, PASSWORD);
        verify(portal, never()).authenticateAndFetchProfile(anyString(), anyString());
        verifyNoInteractions(codes, jwt);
    }

    @Test void reservedAdminRetainsLocalLoginWithoutSchoolOrCodeOperations() {
        var admin = mock(AdminAccountService.class);
        ReflectionTestUtils.setField(auth, "adminAccountService", admin);
        when(admin.matchesUsername(ID)).thenReturn(true);
        when(admin.login(ID, PASSWORD)).thenReturn(new TokenExchangeResult("synthetic-access", "synthetic-refresh"));
        var result = auth.sejongLogin(new SejongLoginCommand(ID, PASSWORD));
        assertThat(result.isNewMember()).isFalse();
        assertThat(result.getVerifiedProfile()).isNull();
        verifyNoInteractions(portal, codes, members, testAccounts, jwt);
    }

    @Test void reservedTestAccountRetainsLocalLoginWithoutSchoolOrCodeOperations() {
        var testAccount = mock(TestAccountService.class);
        when(testAccounts.getIfAvailable()).thenReturn(testAccount);
        when(testAccount.login(TestAccountService.USERNAME, PASSWORD)).thenReturn(new TokenExchangeResult("synthetic-access", "synthetic-refresh"));
        var result = auth.sejongLogin(new SejongLoginCommand(TestAccountService.USERNAME, PASSWORD));
        assertThat(result.isNewMember()).isFalse();
        assertThat(result.getVerifiedProfile()).isNull();
        verifyNoInteractions(portal, codes, members, jwt);
    }
}
