package com.wardk.meeteam_backend.domain.member.service;

import com.wardk.meeteam_backend.web.mainpage.dto.response.MemberCardResponse;
import com.wardk.meeteam_backend.web.member.dto.request.MemberProfileUpdateRequest;
import com.wardk.meeteam_backend.web.member.dto.response.MemberDetailResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileResponse;
import com.wardk.meeteam_backend.web.member.dto.response.MemberProfileUpdateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MemberProfileService {

    MemberProfileResponse profile(Long memberId);

    /**
     * 특정 사용자 상세 조회 (v1).
     */
    MemberDetailResponse getMemberDetail(Long memberId);

    MemberProfileUpdateResponse updateProfile(Long memberId, MemberProfileUpdateRequest request, MultipartFile profileImage);

    /**
     * 메인페이지 유저 카드 목록 조회.
     */
    Page<MemberCardResponse> getMainPageMembers(Pageable pageable);

    /**
     * 팀원 찾기 v1 검색.
     *
     * @param name           이름 검색 (부분 일치)
     * @param jobFieldId     직군 ID (null이면 전체)
     * @param techStackNames 기술스택 이름 목록
     * @param pageable       페이징 및 정렬
     */
    Page<MemberCardResponse> searchMembersV1(String name, Long jobFieldId, List<String> techStackNames, Pageable pageable);
}
