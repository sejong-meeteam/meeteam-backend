package com.wardk.meeteam_backend.domain.job.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 직무(JobPosition) 엔티티.
 * 각 직군(JobField)에 속하는 세부 직무를 정의합니다.
 */
@Entity
@Getter
@Table(name = "job_position")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_position_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_field_id", nullable = false)
    private JobField jobField;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 70)
    private JobPositionCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    private JobPosition(JobField jobField, JobPositionCode code) {
        this.jobField = jobField;
        this.code = code;
        this.name = code.getDisplayName();
    }

    public static JobPosition of(JobField jobField, JobPositionCode code) {
        return new JobPosition(jobField, code);
    }
}
