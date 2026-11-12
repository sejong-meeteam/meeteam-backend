package com.wardk.meeteam_backend.web.member.dto.request;

import com.wardk.meeteam_backend.domain.member.entity.Gender;
import com.wardk.meeteam_backend.web.auth.dto.register.TechStackOrderRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "회원 프로필 수정 요청")
public class MemberProfileUpdateRequest {

    @Size(max = 100, message = "이름은 100자 이하로 입력해주세요.")
    @Schema(description = "학교 확인 계정은 생략 가능하며 변경할 수 없습니다. 기존 계정은 필수입니다.", example = "김철수")
    private String name;

    @Schema(description = "이름 비공개 여부. true이면 일반 응답에서 마스킹하며 해당 지원 프로젝트 리더는 지원서에서 실명을 확인합니다. 생략/null이면 기존 설정을 유지합니다.", example = "true")
    private Boolean namePrivate;

    /** Source compatibility for older Java callers; omitted preference must not reset privacy. */
    public MemberProfileUpdateRequest(String name, Integer age, Gender gender, List<Long> jobPositionIds,
            List<TechStackOrderRequest> techStacks, Boolean isParticipating, String introduction, String githubUrl, String blogUrl) {
        this(name, null, age, gender, jobPositionIds, techStacks, isParticipating, introduction, githubUrl, blogUrl);
    }

    @NotNull(message = "나이를 입력해주세요.")
    @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
    @Max(value = 150, message = "나이는 150 이하여야 합니다.")
    @Schema(description = "회원 나이", example = "25")
    private Integer age;

    @NotNull(message = "성별을 선택해주세요.")
    @Schema(description = "회원 성별", example = "MALE")
    private Gender gender;

    @NotEmpty(message = "관심 분야를 하나 이상 선택해주세요.")
    @Schema(description = "관심 직무 포지션 목록",
        example = "[1, 2]")
    private List<Long> jobPositionIds;

    @NotEmpty(message = "기술 스택을 하나 이상 선택해주세요.")
    @Valid
    @Schema(description = "기술 스택 목록 (displayOrder 포함)",
        example = "[{\"id\": 1, \"displayOrder\": 1}, {\"id\": 2, \"displayOrder\": 2}]")
    private List<TechStackOrderRequest> techStacks;

    @Schema(description = "프로젝트 참여 가능 여부", example = "true")
    private Boolean isParticipating;

    @Size(max = 1000, message = "자기소개는 1000자 이하로 입력해주세요.")
    @Schema(description = "자기소개", example = "백엔드 개발에 관심이 많은 개발자입니다.")
    private String introduction;

    @Schema(description = "GitHub URL", example = "https://github.com/username")
    private String githubUrl;

    @Schema(description = "블로그 URL", example = "https://velog.io/@username")
    private String blogUrl;
}
