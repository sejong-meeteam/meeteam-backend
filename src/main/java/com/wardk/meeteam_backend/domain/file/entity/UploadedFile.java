package com.wardk.meeteam_backend.domain.file.entity;

import com.wardk.meeteam_backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "uploaded_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UploadedFile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false, length = 1000)
    private String fileUrl;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String directory;

    @Column(nullable = true)
    private Long uploaderId; // 업로드한 사용자 ID

    public static UploadedFile create(String fileName, String originalFileName,
                                    String fileUrl, String fileType, Long fileSize,
                                    String directory, Long uploaderId) {
        return UploadedFile.builder()
                .fileName(fileName)
                .originalFileName(originalFileName)
                .fileUrl(fileUrl)
                .fileType(fileType)
                .fileSize(fileSize)
                .directory(directory)
                .uploaderId(uploaderId)
                .build();
    }
}
