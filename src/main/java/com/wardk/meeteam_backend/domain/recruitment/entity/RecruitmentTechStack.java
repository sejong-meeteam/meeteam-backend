package com.wardk.meeteam_backend.domain.recruitment.entity;

import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "recruitment_tech_stack",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_recruitment_tech_stack_pair",
                        columnNames = {"recruitment_state_id", "tech_stack_catalog_id"}
                )
        }
)
public class RecruitmentTechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recruitment_tech_stack_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruitment_state_id", nullable = false)
    private RecruitmentState recruitmentState;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tech_stack_catalog_id", nullable = false)
    private TechStack techStack;

    private RecruitmentTechStack(TechStack techStack) {
        this.techStack = techStack;
    }

    public static RecruitmentTechStack create(TechStack techStack) {
        return new RecruitmentTechStack(techStack);
    }

    public void assignRecruitmentState(RecruitmentState recruitmentState) {
        this.recruitmentState = recruitmentState;
    }
}
