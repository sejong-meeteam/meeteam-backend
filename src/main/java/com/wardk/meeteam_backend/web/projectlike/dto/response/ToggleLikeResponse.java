package com.wardk.meeteam_backend.web.projectlike.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "프로젝트 좋아요 토글 응답")
public class ToggleLikeResponse {

    @Schema(description = "프로젝트 ID", example = "123")
    private Long projectId;

    @Schema(description = "사용자가 현재 좋아요를 누른 상태인지 여부 (true: 좋아요 ON, false: 좋아요 OFF)", example = "true")
    private Boolean liked;

    @Schema(description = "현재 집계된 좋아요 수", example = "12")
    private Integer likeCount;
}
