package com.wardk.meeteam_backend.domain.job.service;

import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import com.wardk.meeteam_backend.domain.job.repository.JobFieldRepository;
import com.wardk.meeteam_backend.web.job.dto.response.JobFieldOptionResponse;
import com.wardk.meeteam_backend.web.job.dto.response.JobOptionResponse;
import com.wardk.meeteam_backend.web.job.dto.response.JobPositionOptionResponse;
import com.wardk.meeteam_backend.web.job.dto.response.TechStackOptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 직군/직무/기술스택 정보 조회 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobInfoServiceImpl implements JobInfoService {

    private final JobFieldRepository jobFieldRepository;

    @Override
    public JobOptionResponse getJobOptions() {
        List<JobField> fields = jobFieldRepository.findAllWithPositions();

        List<JobFieldOptionResponse> fieldResponses = fields.stream()
                .map(this::toFieldResponse)
                .toList();

        return JobOptionResponse.of(fieldResponses);
    }

    private JobFieldOptionResponse toFieldResponse(JobField field) {
        List<JobPositionOptionResponse> positions = field.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getId))
                .map(JobPositionOptionResponse::of)
                .toList();

        List<TechStackOptionResponse> techStacks = field.getJobFieldTechStacks().stream()
                .map(jfts -> TechStackOptionResponse.of(jfts.getTechStack()))
                .distinct()
                .sorted(Comparator.comparing(TechStackOptionResponse::id))
                .toList();

        return JobFieldOptionResponse.of(field, positions, techStacks);
    }
}
