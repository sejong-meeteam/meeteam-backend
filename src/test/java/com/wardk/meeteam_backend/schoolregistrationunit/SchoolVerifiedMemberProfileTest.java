package com.wardk.meeteam_backend.schoolregistrationunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.member.service.MemberProfileServiceImpl;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.member.dto.request.MemberProfileUpdateRequest;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolVerifiedMemberProfileTest {
    @Mock MemberRepository members;
    @Mock S3FileService s3;
    @InjectMocks MemberProfileServiceImpl profiles;

    private Member verified() {
        return Member.createSchoolVerifiedMember("20261234", "School Name", "Computer Science",
                SchoolAuthoritativeAuthServiceTest.command(), "synthetic-hash", "images/existing.png");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"School Name"})
    void verifiedNameIsOmittableOrUnchangedButNotBlank(String name) {
        Member member = verified();
        if ("".equals(name)) {
            assertThatThrownBy(() -> member.updateProfile(name, 27, Gender.FEMALE, false, "new intro", null, null))
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        } else {
            member.updateProfile(name, 27, Gender.FEMALE, false, "new intro", null, null);
            assertThat(member.getAge()).isEqualTo(27);
            assertThat(member.getIntroduction()).isEqualTo("new intro");
        }
        assertThat(member.getRealName()).isEqualTo("School Name");
        assertThat(member.getDepartment()).isEqualTo("Computer Science");
        assertThat(member.isSchoolProfileVerified()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"Impersonation", " School Name", " ", "school name"})
    void differingVerifiedNameIsRejectedWithoutPartialProfileMutation(String name) {
        Member member = verified();
        assertThatThrownBy(() -> member.updateProfile(name, 99, Gender.FEMALE, false, "mutated", null, null))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(member.getRealName()).isEqualTo("School Name");
        assertThat(member.getIntroduction()).isNull();
        assertThat(member.getGender()).isEqualTo(Gender.MALE);
    }

    @Test void rejectedNamePreventsExistingImageDeletionNewUploadAndSave() {
        Member member = verified();
        when(members.findById(42L)).thenReturn(Optional.of(member));
        var request = new MemberProfileUpdateRequest();
        request.setName("Spoofed Name");
        request.setIntroduction("must not be applied");
        var image = new MockMultipartFile("file", "synthetic.png", "image/png", new byte[]{1});
        assertThatThrownBy(() -> profiles.updateProfile(42L, request, image))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(s3);
        verify(members, never()).save(any());
        assertThat(member.getStoreFileName()).isEqualTo("images/existing.png");
        assertThat(member.getIntroduction()).isNull();
    }

    @Test void legacyMemberCanStillChangeNonblankNameWithoutBecomingVerified() {
        Member legacy = Member.createSejongMember("20261234", SchoolAuthoritativeAuthServiceTest.command(), "synthetic-hash", null);
        legacy.updateProfile("Updated Legacy", 30, Gender.FEMALE, false, "updated", null, null);
        assertThat(legacy.getRealName()).isEqualTo("Updated Legacy");
        assertThat(legacy.getDepartment()).isNull();
        assertThat(legacy.isSchoolProfileVerified()).isFalse();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void legacyMemberStillRequiresNonblankName(String name) {
        Member legacy = Member.createForTest("synthetic@example.invalid", "Legacy");
        assertThatThrownBy(() -> legacy.updateProfile(name, 30, Gender.MALE, true, null, null, null))
                .isInstanceOf(CustomException.class);
        assertThat(legacy.getRealName()).isEqualTo("Legacy");
    }

    @Test void officialFieldsArePresentInProfileResponseAndJson() throws Exception {
        var response = new MemberProfileResponse(verified(), 42L);
        assertThat(response.getName()).isEqualTo("School Name");
        assertThat(response.getDepartment()).isEqualTo("Computer Science");
        assertThat(response.isSchoolProfileVerified()).isTrue();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertThat(json.path("name").asText()).isEqualTo("School Name");
        assertThat(json.path("department").asText()).isEqualTo("Computer Science");
        assertThat(json.path("schoolProfileVerified").asBoolean()).isTrue();
    }

    @Test void legacyResponsePreservesNameAndExplicitlyReportsUnverified() {
        var response = new MemberProfileResponse(Member.createForTest("synthetic@example.invalid", "Legacy"), 43L);
        assertThat(response.getName()).isEqualTo("Legacy");
        assertThat(response.getDepartment()).isNull();
        assertThat(response.isSchoolProfileVerified()).isFalse();
    }
}
