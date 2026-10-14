package com.wardk.meeteam_backend.domain.skill.repository;

import com.wardk.meeteam_backend.domain.skill.entity.MemberSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberSkillRepository extends JpaRepository<MemberSkill, Long> {

    /**
     * 특정 회원의 모든 기술스택 삭제
     */
    void deleteByMemberId(Long memberId);
}
