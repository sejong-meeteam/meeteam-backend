package com.wardk.meeteam_backend.domain.file.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String fileName;
    private String originalFileName;
    private String fileUrl;
    private String fileType;
    private Long fileSize;

    public static FileUploadResponse of(String fileName, String originalFileName,
                                      String fileUrl, String fileType, Long fileSize) {
        return FileUploadResponse.builder()
                .fileName(fileName)
                .originalFileName(originalFileName)
                .fileUrl(fileUrl)
                .fileType(fileType)
                .fileSize(fileSize)
                .build();
    }
}
