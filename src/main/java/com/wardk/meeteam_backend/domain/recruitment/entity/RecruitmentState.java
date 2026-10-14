package com.wardk.meeteam_backend.domain.recruitment.entity;

import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.exception.CustomException;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "recruitment_state")
@BatchSize(size = 100)
public class RecruitmentState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recruitment_state_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "recruit_count")
    private Integer recruitmentCount;

    @Column(name = "current_count")
    private Integer currentCount;

    @Column(name = "is_closed", nullable = false)
    private boolean isClosed = false;

    @OneToMany(mappedBy = "recruitmentState", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<RecruitmentTechStack> recruitmentTechStacks = new ArrayList<>();

    /**
     * JobField는 JobPosition을 통해 접근합니다.
     */
    public JobField getJobField() {
        return this.jobPosition.getJobField();
    }

    /**
     * 해당 포지션의 모집이 마감되었는지 확인합니다.
     * 수동 마감이거나 모집 인원이 다 찼으면 true.
     */
    public boolean isClosed() {
        return this.isClosed || this.currentCount >= this.recruitmentCount;
    }

    /**
     * 포지션 모집을 마감합니다.
     */
    public void close() {
        this.isClosed = true;
    }

    /**
     * 포지션 모집을 재개합니다.
     */
    public void reopen() {
        this.isClosed = false;
    }

    public void assignProject(Project project) {
        this.project = project;
    }

    public void increaseCurrentCount() {
        if (isClosed()) {
            throw new CustomException(ErrorCode.RECRUITMENT_FULL);
        }
        this.currentCount++;
    }

    /**
     * 팀원 방출/탈퇴 시 현재 인원을 감소시킵니다.
     */
    public void decreaseCurrentCount() {
        if (this.currentCount > 0) {
            this.currentCount--;
        }
    }

    public void updateCurrentCount(Integer currentCount) {
        if (currentCount < 0 || currentCount > this.recruitmentCount) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        this.currentCount = currentCount;
    }

    public void updateRecruitmentCount(Integer newRecruitmentCount) {
        if (newRecruitmentCount < 0) {
            throw new CustomException(ErrorCode.INVALID_RECRUITMENT_COUNT);
        }
        if (newRecruitmentCount < this.currentCount) {
            throw new CustomException(ErrorCode.INVALID_RECRUITMENT_COUNT);
        }
        this.recruitmentCount = newRecruitmentCount;
    }

    public void addRecruitmentTechStack(RecruitmentTechStack recruitmentTechStack) {
        this.recruitmentTechStacks.add(recruitmentTechStack);
        recruitmentTechStack.assignRecruitmentState(this);
    }

    /**
     * 기존 기술 스택을 모두 제거합니다.
     * 주의: orphanRemoval=true이므로 clear() 후 flush()를 호출해야 DELETE가 실행됩니다.
     */
    public void clearTechStacks() {
        this.recruitmentTechStacks.clear();
    }

    /**
     * 기존 기술 스택을 모두 제거하고 새 기술 스택으로 교체합니다.
     * 주의: JPA 실행 순서 문제로 unique constraint 위반이 발생할 수 있으므로,
     * clearTechStacks() + flush() + addRecruitmentTechStack() 방식을 사용하세요.
     */
    @Deprecated
    public void replaceTechStacks(List<RecruitmentTechStack> newTechStacks) {
        this.recruitmentTechStacks.clear();
        for (RecruitmentTechStack techStack : newTechStacks) {
            addRecruitmentTechStack(techStack);
        }
    }

    @Builder
    public RecruitmentState(JobPosition jobPosition, Integer recruitmentCount) {
        this.jobPosition = jobPosition;
        this.recruitmentCount = recruitmentCount;
        this.currentCount = 0;
        this.isClosed = false;
    }

    public static RecruitmentState createRecruitmentState(JobPosition jobPosition, Integer recruitmentCount) {
        return RecruitmentState.builder()
                .jobPosition(jobPosition)
                .recruitmentCount(recruitmentCount)
                .build();
    }
}
