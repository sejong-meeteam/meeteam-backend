package com.wardk.meeteam_backend.web.mainpage.dto.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.recruitment.entity.RecruitmentState;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectCardResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long projectId;
	private String projectName;
	private String categoryName; // AI/테크 (한글 displayName)
	private String categoryCode; // AI_TECH (enum name)
	private String platformName; // WEB
	private String imageUrl; // 커버 이미지
	private LocalDate endDate; // 마감일
	private String creatorName; // 리더 이름
	private String creatorImageUrl; // 리더 프로필 이미지
	private int currentCount; // 현재 합류 인원 (전체 합산)
	private int recruitmentCount; // 모집 정원 (전체 합산)
	private boolean isLiked; // 좋아요 여부
	private int likeCount; // 좋아요 수
	private List<RecruitmentPositionResponse> recruitments; // 포지션별 모집 현황

	public static ProjectCardResponse from(Project project, List<RecruitmentState> recs, boolean isLiked) {
		int currentCount = recs.stream().mapToInt(RecruitmentState::getCurrentCount).sum();
		int recruitmentCount = recs.stream().mapToInt(RecruitmentState::getRecruitmentCount).sum();

		List<RecruitmentPositionResponse> recruitments = recs.stream()
			.map(RecruitmentPositionResponse::from)
			.toList();

		return ProjectCardResponse.builder()
			.projectId(project.getId())
			.projectName(project.getName())
			.categoryName(project.getCategoryDisplayName())
			.categoryCode(project.getCategoryCode())
			.platformName(project.getPlatformName())
			.imageUrl(project.getImageUrl())
			.endDate(project.getEndDate())
			.creatorName(project.getCreator().getRealName())
			.creatorImageUrl(project.getCreator().getStoreFileName())
			.currentCount(currentCount)
			.recruitmentCount(recruitmentCount)
			.isLiked(isLiked)
			.likeCount(project.getLikeCount())
			.recruitments(recruitments)
			.build();
	}
}
