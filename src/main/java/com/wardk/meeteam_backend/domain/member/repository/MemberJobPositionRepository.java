package com.wardk.meeteam_backend.domain.member.repository;

import com.wardk.meeteam_backend.domain.member.entity.MemberJobPosition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJobPositionRepository extends JpaRepository<MemberJobPosition, Long> {

    /**
     * 특정 회원의 모든 직무 포지션 삭제
     */
    void deleteByMemberId(Long memberId);
}