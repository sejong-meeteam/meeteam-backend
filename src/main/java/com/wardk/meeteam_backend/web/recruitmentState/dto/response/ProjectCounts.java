package com.wardk.meeteam_backend.web.recruitmentState.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProjectCounts {
    private Long currentCount;
    private Long recruitmentCount;
}