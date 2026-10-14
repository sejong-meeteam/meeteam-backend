package com.wardk.meeteam_backend.web.project.dto.request;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.domain.project.entity.Recruitment;
import lombok.Data;

@Data
public class ProjectSearchCondition {

    private ProjectCategory projectCategory;

    private Recruitment recruitment;

    private PlatformCategory platformCategory;

    private JobFieldCode jobFieldCode;

    private String keyword;
}
