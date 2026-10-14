package com.wardk.meeteam_backend.domain.job.repository;

import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JobFieldRepository extends JpaRepository<JobField, Long> {
    Optional<JobField> findByCode(JobFieldCode code);

    @Query("SELECT DISTINCT jf FROM JobField jf " +
           "LEFT JOIN FETCH jf.jobPositions " +
           "ORDER BY jf.id")
    List<JobField> findAllWithPositions();
}
