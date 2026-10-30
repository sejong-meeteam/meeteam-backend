package com.wardk.meeteam_backend.s3unit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.wardk.meeteam_backend.domain.file.entity.UploadedFile;
import com.wardk.meeteam_backend.domain.file.repository.UploadedFileRepository;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3FileServiceTest {
    private AmazonS3 s3;
    private UploadedFileRepository repository;
    private S3FileService service;
    private static final String BASE = "https://bucket.example.invalid";

    @BeforeEach
    void setUp() {
        s3 = mock(AmazonS3.class);
        repository = mock(UploadedFileRepository.class);
        service = new S3FileService(s3, repository);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "allowedExtensions", "png,jpg");
        ReflectionTestUtils.setField(service, "maxSize", "1KB");
        ReflectionTestUtils.setField(service, "baseUrl", BASE);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void savesCanonicalUrlUploaderAndMetadataUnderAllowedPrefixes() {
        for (String directory : new String[]{"images", "profiles", "images/profiles"}) {
            String expectedDirectory = directory.equals("profiles") ? "images/profiles" : directory;
            String url = service.uploadFile(image(), directory, 42L);
            ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
            ArgumentCaptor<UploadedFile> saved = ArgumentCaptor.forClass(UploadedFile.class);
            verify(s3).putObject(put.capture());
            verify(repository).save(saved.capture());
            assertEquals("test-bucket", put.getValue().getBucketName());
            assertTrue(put.getValue().getKey().startsWith(expectedDirectory + "/"));
            assertTrue(put.getValue().getKey().endsWith(".png"));
            assertEquals(BASE + "/" + put.getValue().getKey(), url);
            assertEquals("image/png", put.getValue().getMetadata().getContentType());
            assertEquals(3L, put.getValue().getMetadata().getContentLength());
            UploadedFile entity = saved.getValue();
            assertEquals(url, entity.getFileUrl());
            assertEquals(expectedDirectory, entity.getDirectory());
            assertEquals("photo.png", entity.getOriginalFileName());
            assertEquals(put.getValue().getKey().substring(expectedDirectory.length() + 1), entity.getFileName());
            assertEquals(42L, entity.getUploaderId());
            assertEquals(3L, entity.getFileSize());
            assertEquals("image/png", entity.getFileType());
            clearInvocations(s3, repository);
        }
    }

    @Test
    void rejectsInvalidFilesAndDirectoriesBeforeAwsOrDatabase() {
        assertFailure(ErrorCode.EMPTY_FILE, () -> service.uploadFile(null, "images"));
        assertFailure(ErrorCode.EMPTY_FILE, () -> service.uploadFile(new MockMultipartFile("file", new byte[0]), "images"));
        assertFailure(ErrorCode.INVALID_FILE_EXTENSION, () -> service.uploadFile(new MockMultipartFile("file", "a.exe", "image/png", new byte[]{1}), "images"));
        assertFailure(ErrorCode.FILE_SIZE_EXCEEDED, () -> service.uploadFile(new MockMultipartFile("file", "a.png", "image/png", new byte[1025]), "images"));
        for (String directory : new String[]{null, "other", "images/../other", "images/nested"}) {
            assertFailure(ErrorCode.INVALID_REQUEST, () -> service.uploadFile(image(), directory));
        }
        verifyNoInteractions(s3, repository);
    }

    @Test
    void convertsAwsFailureWithoutSaving() {
        when(s3.putObject(any(PutObjectRequest.class))).thenThrow(new AmazonClientException("offline failure"));
        assertFailure(ErrorCode.FILE_UPLOAD_FAILED, () -> service.uploadFile(image(), "images"));
        verify(repository, never()).save(any());
        verify(s3, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void convertsInputStreamFailureWithoutAwsOrSave() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getSize()).thenReturn(3L);
        when(file.getInputStream()).thenThrow(new IOException("offline failure"));
        assertFailure(ErrorCode.FILE_UPLOAD_FAILED, () -> service.uploadFile(file, "images"));
        verifyNoInteractions(s3, repository);
    }

    @Test
    void rollbackDeletesOnlyNewObjectEvenWhenDatabaseSaveFails() {
        TransactionSynchronizationManager.initSynchronization();
        when(repository.save(any())).thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> service.uploadFile(image(), "profiles", 42L));
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture());
        verify(s3, never()).deleteObject(anyString(), anyString());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(s3).deleteObject("test-bucket", put.getValue().getKey());
        verifyNoMoreInteractions(s3);
        verify(repository, never()).deleteByFileUrl(anyString());
    }

    @Test
    void committedTransactionKeepsNewObject() {
        TransactionSynchronizationManager.initSynchronization();
        service.uploadFile(image(), "images");
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(s3, never()).deleteObject(anyString(), anyString());
        verify(repository).save(any());
    }

    @Test
    void databaseFailureWithoutSynchronizationCleansUpOnlyTheNewObject() {
        when(repository.save(any())).thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> service.uploadFile(image(), "images"));
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture());
        verify(s3).deleteObject("test-bucket", put.getValue().getKey());
        verify(repository, never()).deleteByFileUrl(anyString());
        verifyNoMoreInteractions(s3);
    }

    @Test
    void rejectsUnownedAndUnsafeDeletionUrlsBeforeChangingAnything() {
        for (String url : new String[]{null, "images/a.png", "https://other.example.invalid/images/a.png",
                BASE + ".evil.invalid/images/a.png", BASE + "/other/a.png",
                BASE + "/images/../a.png", BASE + "/images/%2e%2e/a.png",
                BASE + "/images//a.png", BASE + "/images/a.png?download=1"}) {
            assertFailure(ErrorCode.INVALID_REQUEST, () -> service.deleteFile(url));
        }
        verifyNoInteractions(s3, repository);
    }

    @Test
    void deletesCanonicalOwnedObjectWithoutAnOuterTransaction() {
        String url = BASE + "/images/a.png";
        service.deleteFile(url);
        verify(s3).deleteObject("test-bucket", "images/a.png");
        verify(repository).deleteByFileUrl(url);
    }

    @Test
    void deletesOldPhotoOnlyAfterTheOuterTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        String url = BASE + "/images/profiles/old.png";
        service.deleteFile(url);
        verify(repository).deleteByFileUrl(url);
        verifyNoInteractions(s3);
        var synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(s3).deleteObject("test-bucket", "images/profiles/old.png");
        verifyNoMoreInteractions(s3);
    }

    @Test
    void failedProfileUpdatePreservesTheOldPhoto() {
        TransactionSynchronizationManager.initSynchronization();
        service.deleteFile(BASE + "/images/profiles/old.png");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(s3);
    }

    private void assertFailure(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(CustomException.class, action).getErrorCode());
    }
}
