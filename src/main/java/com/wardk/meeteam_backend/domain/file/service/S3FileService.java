package com.wardk.meeteam_backend.domain.file.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.wardk.meeteam_backend.domain.file.entity.UploadedFile;
import com.wardk.meeteam_backend.domain.file.repository.UploadedFileRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class S3FileService {

    private final AmazonS3 amazonS3;

    private final UploadedFileRepository uploadedFileRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${file.upload.allowed-extensions}")
    private String allowedExtensions;

    @Value("${file.upload.max-size}")
    private String maxSize;

    @Value("${file.upload.s3.base-url}")
    private String baseUrl;

    public String uploadFile(MultipartFile file, String directory) {

        return uploadFile(file, directory, null);
    }

    public String uploadFile(MultipartFile file, String directory, Long uploaderId) {
        validateFile(file);

        // Keep profile uploads inside the role's least-privileged images/* scope.
        if ("profiles".equals(directory)) {
            directory = "images/profiles";
        }
        if (!"images".equals(directory) && !"images/profiles".equals(directory)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        String fileName = generateFileName(file.getOriginalFilename());
        String filePath = directory + "/" + fileName;

        boolean uploaded = false;
        boolean rollbackCleanupRegistered = false;
        try (InputStream input = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    filePath,
                    input,
                    metadata
            );

            amazonS3.putObject(putObjectRequest);
            uploaded = true;
            rollbackCleanupRegistered = cleanUpOnRollback(filePath);

            String fileUrl = baseUrl + "/" + filePath;

            // 파일 업로드 기록 저장
            UploadedFile uploadedFile = UploadedFile.create(
                    fileName,
                    file.getOriginalFilename(),
                    fileUrl,
                    file.getContentType(),
                    file.getSize(),
                    directory,
                    uploaderId
            );
            uploadedFileRepository.save(uploadedFile);

            log.info("파일 업로드 성공: {}", fileUrl);

            return fileUrl;

        } catch (IOException | AmazonClientException e) {
            if (uploaded && !rollbackCleanupRegistered) {
                cleanUpObject(filePath);
            }
            // Do not log AWS exception messages, request headers or credentials.
            log.error("파일 업로드 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (RuntimeException e) {
            if (uploaded && !rollbackCleanupRegistered) {
                cleanUpObject(filePath);
            }
            throw e;
        }
    }

    public void deleteFile(String fileUrl) {
        String key = extractFileNameFromUrl(fileUrl);
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                // Keep the old photo until the entire profile update commits successfully.
                uploadedFileRepository.deleteByFileUrl(fileUrl);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanUpObject(key);
                    }
                });
                log.info("커밋 후 파일 삭제 예약: {}", key);
            } else {
                amazonS3.deleteObject(bucketName, key);
                uploadedFileRepository.deleteByFileUrl(fileUrl);
                log.info("파일 삭제 성공: {}", key);
            }
        } catch (Exception e) {
            log.error("파일 삭제 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    @Transactional(readOnly = true)
    public List<UploadedFile> getFilesByUploader(Long uploaderId) {
        return uploadedFileRepository.findByUploaderId(uploaderId);
    }

    @Transactional(readOnly = true)
    public List<UploadedFile> getFilesByDirectory(String directory) {
        return uploadedFileRepository.findByDirectory(directory);
    }

    @Transactional(readOnly = true)
    public List<UploadedFile> getAllFiles() {
        return uploadedFileRepository.findAll();
    }

    private boolean cleanUpOnRollback(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        cleanUpObject(key);
                    }
                }
            });
            return true;
        }
        return false;
    }

    private void cleanUpObject(String key) {
        try {
            amazonS3.deleteObject(bucketName, key);
        } catch (AmazonClientException exception) {
            // The transaction outcome is already decided; retain a safe diagnostic for cleanup retry.
            log.error("이미지 정리 실패 - key: {}, type: {}", key, exception.getClass().getSimpleName());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_FILE);
        }

        // 파일 확장자 검증
        String fileName = file.getOriginalFilename();
        if (fileName == null || !isValidExtension(fileName)) {
            throw new CustomException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        // 파일 크기 검증
        long maxSizeBytes = parseMaxSize(maxSize);
        if (file.getSize() > maxSizeBytes) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private boolean isValidExtension(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        List<String> allowedExtensionList = Arrays.asList(allowedExtensions.split(","));
        return allowedExtensionList.contains(extension);
    }

    private long parseMaxSize(String maxSize) {
        if (maxSize.endsWith("MB")) {
            return Long.parseLong(maxSize.replace("MB", "")) * 1024 * 1024;
        } else if (maxSize.endsWith("KB")) {
            return Long.parseLong(maxSize.replace("KB", "")) * 1024;
        } else {
            return Long.parseLong(maxSize);
        }
    }

    private String generateFileName(String originalFileName) {
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        return UUID.randomUUID().toString() + extension;
    }

    private String extractFileNameFromUrl(String fileUrl) {
        String prefix = baseUrl.replaceAll("/+$", "") + "/";
        if (fileUrl == null || !fileUrl.startsWith(prefix)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        String key = fileUrl.substring(prefix.length());
        if (!S3ImageKeys.isAllowed(key)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return key;
    }
}
