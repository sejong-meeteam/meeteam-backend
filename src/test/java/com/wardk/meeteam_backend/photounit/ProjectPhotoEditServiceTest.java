package com.wardk.meeteam_backend.photounit;

import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.job.repository.TechStackRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.ProjectEditServiceImpl;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static com.wardk.meeteam_backend.photounit.ProjectPhotoFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectPhotoEditServiceTest {
    @Mock S3FileService s3;
    @Mock ProjectRepository projects;
    @Mock MemberRepository members;
    @Mock ProjectMemberRepository projectMembers;
    @Mock ProjectApplicationRepository applications;
    @Mock JobPositionRepository positions;
    @Mock TechStackRepository techStacks;
    @Mock EntityManager entityManager;
    private ProjectEditServiceImpl service;
    private Member leader;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectEditServiceImpl(s3, projects, members, projectMembers,
                applications, positions, techStacks, entityManager);
        leader = leader();
        project = project(leader, OLD_URL);
    }

    private void found() {
        when(projects.findProjectForEdit(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private void editable() {
        found();
        when(members.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(leader));
        when(techStacks.findByIdIn(List.of(TECH_ID))).thenReturn(List.of(techStack()));
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "new.png", "image/png", new byte[]{1, 2, 3});
    }

    private void noDownstreamEffects() {
        verifyNoInteractions(s3, members, projectMembers, applications, positions, techStacks, entityManager);
        verify(projects, never()).save(any());
        assertEquals(OLD_URL, project.getImageUrl());
        assertEquals("Original project", project.getName());
        assertEquals(1, project.getRecruitments().size());
        assertEquals(1, project.getRecruitments().get(0).getRecruitmentTechStacks().size());
    }

    @Test
    void leaderRemovalClearsUrlAndSavesBeforeDeletingPreviousObject() {
        editable();
        doAnswer(invocation -> {
            assertNull(((Project) invocation.getArgument(0)).getImageUrl());
            return project;
        }).when(projects).save(project);
        var response = service.update(PROJECT_ID, command(true), null, EMAIL);
        assertNull(project.getImageUrl());
        assertEquals("Edited project", project.getName());
        assertEquals(PROJECT_ID, response.projectId());
        assertEquals(0, response.autoRejectedApplicantCount());
        var order = inOrder(projects, s3);
        order.verify(projects).findProjectForEdit(PROJECT_ID);
        order.verify(projects).save(project);
        order.verify(s3).deleteFile(OLD_URL);
        verifyNoMoreInteractions(s3);
    }

    @Test
    void nullFileWithoutRemovalPreservesExistingUrl() {
        editable();
        service.update(PROJECT_ID, command(false), null, EMAIL);
        assertEquals(OLD_URL, project.getImageUrl());
        verify(projects).save(project);
        verifyNoInteractions(s3);
    }

    @Test
    void emptyFileWithoutRemovalPreservesExistingUrl() {
        editable();
        service.update(PROJECT_ID, command(false), new MockMultipartFile("file", new byte[0]), EMAIL);
        assertEquals(OLD_URL, project.getImageUrl());
        verify(projects).save(project);
        verifyNoInteractions(s3);
    }

    @Test
    void removalWithEmptyFileIsAllowedAndDoesNotUpload() {
        editable();
        service.update(PROJECT_ID, command(true), new MockMultipartFile("file", new byte[0]), EMAIL);
        assertNull(project.getImageUrl());
        verify(s3).deleteFile(OLD_URL);
        verifyNoMoreInteractions(s3);
    }

    @Test
    void replacementWithoutRemovalUsesExistingUploadBehaviorWithoutDeletingOldObject() {
        editable();
        var file = image();
        String replacement = BASE + "/images/new.png";
        when(s3.uploadFile(file, "images", MEMBER_ID)).thenReturn(replacement);
        service.update(PROJECT_ID, command(false), file, EMAIL);
        assertEquals(replacement, project.getImageUrl());
        var order = inOrder(s3, projects);
        order.verify(s3).uploadFile(file, "images", MEMBER_ID);
        order.verify(projects).save(project);
        verifyNoMoreInteractions(s3);
    }

    @Test
    void repeatedRemovalOfAlreadyNullUrlDoesNotCallS3Again() {
        editable();
        service.update(PROJECT_ID, command(true), null, EMAIL);
        service.update(PROJECT_ID, command(true), null, EMAIL);
        assertNull(project.getImageUrl());
        verify(projects, times(2)).save(project);
        verify(s3, times(1)).deleteFile(OLD_URL);
        verifyNoMoreInteractions(s3);
    }

    @Test
    void initiallyNullImageRemovalDoesNotCallS3() {
        project = project(leader, null);
        editable();
        service.update(PROJECT_ID, command(true), null, EMAIL);
        assertNull(project.getImageUrl());
        verify(projects).save(project);
        verifyNoInteractions(s3);
    }

    @Test
    void nonLeaderGets403BeforeConflictingFileValidationOrAnySideEffects() {
        found();
        CustomException exception = assertThrows(CustomException.class,
                () -> service.update(PROJECT_ID, command(true), image(), "outsider@example.invalid"));
        assertEquals(ErrorCode.PROJECT_MEMBER_FORBIDDEN, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getErrorCode().getStatus());
        noDownstreamEffects();
    }

    @Test
    void suspendedProjectIsDeniedBeforeConflictingFileValidationOrAnySideEffects() {
        project.suspend();
        found();
        CustomException exception = assertThrows(CustomException.class,
                () -> service.update(PROJECT_ID, command(true), image(), EMAIL));
        assertEquals(ErrorCode.PROJECT_EDIT_NOT_ALLOWED_SUSPENDED, exception.getErrorCode());
        noDownstreamEffects();
    }

    @Test
    void removalAndNonemptyFileGets400BeforeUploadOrAnySideEffects() {
        found();
        CustomException exception = assertThrows(CustomException.class,
                () -> service.update(PROJECT_ID, command(true), image(), EMAIL));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getErrorCode().getStatus());
        noDownstreamEffects();
    }

    @Test
    void missingProjectStopsBeforeMemberLookupOrS3() {
        when(projects.findProjectForEdit(PROJECT_ID)).thenReturn(Optional.empty());
        CustomException exception = assertThrows(CustomException.class,
                () -> service.update(PROJECT_ID, command(true), null, EMAIL));
        assertEquals(ErrorCode.PROJECT_NOT_FOUND, exception.getErrorCode());
        noDownstreamEffects();
    }

    @Test
    void databaseSaveFailureDoesNotRequestOldObjectDeletion() {
        editable();
        IllegalStateException failure = new IllegalStateException("offline database failure");
        when(projects.save(project)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.update(PROJECT_ID, command(true), null, EMAIL)));
        verify(projects).save(project);
        verifyNoInteractions(s3);
        // A direct unit call mutates the entity; this does not assert database rollback.
    }
}
