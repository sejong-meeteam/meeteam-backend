package com.wardk.meeteam_backend.web.member.dto.response;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "회원 프로필 수정 응답")
public class MemberProfileUpdateResponse {

    @Schema(description = "회원 ID", example = "1")
    private Long memberId;

    @Schema(description = "수정된 회원 이름", example = "김철수")
    private String name;

    @Schema(description = "수정 성공 메시지", example = "프로필이 성공적으로 수정되었습니다.")
    private String message;

    @Schema(description = "프로필 이미지 URL")
    private String profileImageUrl;

    public MemberProfileUpdateResponse(Member member) {
        this.memberId = member.getId();
        this.name = member.getRealName();
        this.profileImageUrl = member.getStoreFileName();
        this.message = "프로필이 성공적으로 수정되었습니다.";
    }
}
