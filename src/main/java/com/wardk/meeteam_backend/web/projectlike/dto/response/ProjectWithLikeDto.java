package com.wardk.meeteam_backend.web.projectlike.dto.response;

import com.wardk.meeteam_backend.domain.project.entity.PlatformCategory;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.entity.ProjectCategory;
import com.wardk.meeteam_backend.web.project.dto.response.*;
import com.wardk.meeteam_backend.web.projectmember.dto.response.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@Data
public class ProjectWithLikeDto {
    private String name;
    private String description;
    private long likeCount;
    private PlatformCategory platformCategory;
    private ProjectCategory projectCategory;
    private String imageUrl;
    private LocalDate startDate; // 게시일이랑 프로젝트 시작일이랑 똑같은 건가?
    private LocalDate endDate;
    private List<ProjectMemberListResponse> projectMembers;
    private List<String> skills;
    private List<RecruitmentResponse> recruitments;

    public ProjectWithLikeDto(Project project, long likeCount) {
        this.name = project.getName();
        this.description = project.getDescription();
        this.likeCount = likeCount;
        this.platformCategory = project.getPlatformCategory();
        this.projectCategory = project.getProjectCategory();
        this.imageUrl = project.getImageUrl();
        this.startDate = project.getStartDate();
        this.endDate = project.getEndDate();
        this.projectMembers = project.getMembers().stream()
                .map(member -> ProjectMemberListResponse.responseDto(
                        member.getMember().getId(),
                        member.getMember().getRealName(),
                        member.getMember().getStoreFileName(),
                        project.getCreator().getId().equals(member.getMember().getId())
                ))
                .toList();
        this.skills = project.getProjectSkills().stream()
                .map(ps -> ps.getSkill().getSkillName())
                .toList();
        this.recruitments = project.getRecruitments().stream()
                .map(RecruitmentResponse::responseDto)
                .toList();
    }
}
