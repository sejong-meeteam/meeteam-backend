package com.wardk.meeteam_backend.domain.file.dto.response;

import com.wardk.meeteam_backend.domain.file.entity.UploadedFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileListResponse {

    private Long id;
    private String fileName;
    private String originalFileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;
    private String directory;
    private LocalDateTime uploadedAt;

    public static FileListResponse from(UploadedFile uploadedFile) {
        return FileListResponse.builder()
                .id(uploadedFile.getId())
                .fileName(uploadedFile.getFileName())
                .originalFileName(uploadedFile.getOriginalFileName())
                .fileUrl(uploadedFile.getFileUrl())
                .fileType(uploadedFile.getFileType())
                .fileSize(uploadedFile.getFileSize())
                .directory(uploadedFile.getDirectory())
                .uploadedAt(uploadedFile.getCreatedAt())
                .build();
    }
}
