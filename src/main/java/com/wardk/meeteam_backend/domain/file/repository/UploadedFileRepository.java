package com.wardk.meeteam_backend.domain.file.repository;

import com.wardk.meeteam_backend.domain.file.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    Optional<UploadedFile> findByFileUrl(String fileUrl);

    List<UploadedFile> findByUploaderId(Long uploaderId);

    List<UploadedFile> findByDirectory(String directory);

    void deleteByFileUrl(String fileUrl);
}
