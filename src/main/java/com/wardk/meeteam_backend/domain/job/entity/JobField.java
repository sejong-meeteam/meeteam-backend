package com.wardk.meeteam_backend.domain.job.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * 직군(JobField) 엔티티.
 * 기획, 디자인, 프론트, 백엔드, AI, 인프라/운영 등의 직군을 정의합니다.
 */
@Entity
@Getter
@Table(name = "job_field")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_field_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private JobFieldCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "jobField", fetch = FetchType.LAZY)
    private final List<JobPosition> jobPositions = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "jobField", fetch = FetchType.LAZY)
    private final List<JobFieldTechStack> jobFieldTechStacks = new ArrayList<>();

    private JobField(JobFieldCode code) {
        this.code = code;
        this.name = code.getDisplayName();
    }

    public static JobField of(JobFieldCode code) {
        return new JobField(code);
    }
}
