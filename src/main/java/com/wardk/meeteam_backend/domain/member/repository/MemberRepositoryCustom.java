package com.wardk.meeteam_backend.domain.member.repository;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MemberRepositoryCustom {

    /**
     * 팀원 찾기 v1 검색.
     *
     * @param name           이름 검색 (부분 일치, null이면 무시)
     * @param jobFieldId     직군 ID (null이면 전체)
     * @param techStackNames 기술스택 이름 목록 (null이면 무시)
     * @param pageable       페이징 및 정렬 정보
     */
    Page<Member> searchMembersV1(String name, Long jobFieldId, List<String> techStackNames, Pageable pageable);

    /**
     * 메인페이지 회원 목록 조회 (projectCount 정렬 지원).
     *
     * @param pageable 페이징 및 정렬 정보 (sort=projectCount,desc 지원)
     */
    Page<Member> findAllWithProjectCountSort(Pageable pageable);
}

