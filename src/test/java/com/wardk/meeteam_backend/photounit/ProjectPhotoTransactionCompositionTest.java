package com.wardk.meeteam_backend.photounit;

import com.amazonaws.services.s3.AmazonS3;
import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.file.repository.UploadedFileRepository;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.job.repository.TechStackRepository;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.project.service.ProjectEditServiceImpl;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static com.wardk.meeteam_backend.photounit.ProjectPhotoFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Composes the real edit and file services with mocked persistence/AWS and manual callbacks.
 * No transaction proxy or database is started: these tests prove callback wiring, not DB rollback.
 */
@ExtendWith(MockitoExtension.class)
class ProjectPhotoTransactionCompositionTest {
    @Mock AmazonS3 aws;
    @Mock UploadedFileRepository uploadedFiles;
    @Mock ProjectRepository projects;
    @Mock MemberRepository members;
    @Mock ProjectMemberRepository projectMembers;
    @Mock ProjectApplicationRepository applications;
    @Mock JobPositionRepository positions;
    @Mock TechStackRepository techStacks;
    @Mock EntityManager entityManager;
    private ProjectEditServiceImpl service;
    private Project project;

    @BeforeEach
    void setUp() {
        S3FileService files = new S3FileService(aws, uploadedFiles);
        ReflectionTestUtils.setField(files, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(files, "baseUrl", BASE);
        service = new ProjectEditServiceImpl(files, projects, members, projectMembers,
                applications, positions, techStacks, entityManager);
        var leader = leader();
        project = project(leader, OLD_URL);
        when(projects.findProjectForEdit(PROJECT_ID)).thenReturn(Optional.of(project));
        when(members.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(leader));
        when(techStacks.findByIdIn(List.of(TECH_ID))).thenReturn(List.of(techStack()));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void editQueuesOldObjectOnlyAfterProjectSaveAndDeletesItOnlyAtCommit() {
        service.update(PROJECT_ID, command(true), null, EMAIL);
        assertNull(project.getImageUrl());
        var order = inOrder(projects, uploadedFiles);
        order.verify(projects).findProjectForEdit(PROJECT_ID);
        order.verify(projects).save(project);
        order.verify(uploadedFiles).deleteByFileUrl(OLD_URL);
        verifyNoInteractions(aws);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, callbacks.size());
        callbacks.forEach(TransactionSynchronization::afterCommit);
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(aws).deleteObject("test-bucket", "images/old.png");
        verifyNoMoreInteractions(aws);
    }

    @Test
    void simulatedOuterRollbackNeverDeletesOldObjectAfterSuccessfulEdit() {
        service.update(PROJECT_ID, command(true), null, EMAIL);
        verify(projects).save(project);
        verify(uploadedFiles).deleteByFileUrl(OLD_URL);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, callbacks.size());
        callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(aws);
        // Mock persistence has no rollback; only preservation of the external object is asserted.
    }

    @Test
    void failedProjectSaveRegistersNoDeletionCallbackOrMetadataDeletion() {
        when(projects.save(project)).thenThrow(new IllegalStateException("offline database failure"));
        assertThrows(IllegalStateException.class,
                () -> service.update(PROJECT_ID, command(true), null, EMAIL));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verifyNoInteractions(aws, uploadedFiles);
    }
}
