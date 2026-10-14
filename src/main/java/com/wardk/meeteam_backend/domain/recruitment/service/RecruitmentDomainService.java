package com.wardk.meeteam_backend.domain.recruitment.service;

import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentTechStack;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import com.wardk.meeteam_backend.domain.job.repository.JobFieldRepository;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.job.repository.TechStackRepository;
import com.wardk.meeteam_backend.domain.project.service.dto.RecruitmentCommand;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 모집 정보 생성 및 검증을 담당하는 도메인 서비스.
 * 직군, 직무, 기술 스택 간의 비즈니스 규칙을 검증합니다.
 * JobField와 JobPosition은 ENUM 코드로 조회합니다.
 */
@Service
@RequiredArgsConstructor
public class RecruitmentDomainService {

    private final JobFieldRepository jobFieldRepository;
    private final JobPositionRepository jobPositionRepository;
    private final TechStackRepository techStackRepository;

    /**
     * 모집 커맨드 목록으로부터 RecruitmentState 목록을 생성합니다.
     *
     * @param commands 모집 커맨드 목록
     * @return 생성된 RecruitmentState 목록
     */
    public List<RecruitmentState> createRecruitmentStates(List<RecruitmentCommand> commands) {
        return commands.stream()
                .map(command -> createRecruitmentState(command))
                .toList();
    }

    private RecruitmentState createRecruitmentState(RecruitmentCommand command) {
        JobField jobField = findJobFieldByCode(command.jobFieldCode());
        JobPosition jobPosition = findJobPositionByCode(command.jobPositionCode());

        validatePositionBelongsToField(jobPosition, jobField);

        RecruitmentState recruitmentState = RecruitmentState.createRecruitmentState(
                jobPosition, command.recruitmentCount());

        addTechStacksToRecruitment(recruitmentState, command.techStackIds(), jobField);

        return recruitmentState;
    }

    private JobField findJobFieldByCode(JobFieldCode code) {
        return jobFieldRepository.findByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_FIELD_NOT_FOUND));
    }

    private JobPosition findJobPositionByCode(JobPositionCode code) {
        return jobPositionRepository.findByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSITION_NOT_FOUND));
    }

    private void validatePositionBelongsToField(JobPosition jobPosition, JobField jobField) {
        if (!jobPosition.getJobField().getId().equals(jobField.getId())) {
            throw new CustomException(ErrorCode.IS_NOT_ALLOWED_POSITION);
        }
    }

    private void addTechStacksToRecruitment(RecruitmentState recruitmentState, List<Long> techStackIds, JobField jobField) {
        List<TechStack> techStacks = techStackRepository.findByIdIn(techStackIds);

        for (TechStack techStack : techStacks) {
            recruitmentState.addRecruitmentTechStack(RecruitmentTechStack.create(techStack));
        }
    }
}