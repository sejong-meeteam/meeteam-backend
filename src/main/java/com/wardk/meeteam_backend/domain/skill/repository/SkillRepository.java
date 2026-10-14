package com.wardk.meeteam_backend.domain.skill.repository;

import com.wardk.meeteam_backend.domain.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findBySkillName(String skillName);

    @Query(value = "SELECT * FROM skill ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Skill> findRandomSkills(@Param("limit") int limit);

    List<Skill> findBySkillNameIn(List<String> skillNames);
}
