package com.wardk.meeteam_backend.domain.skill.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_id")
    private Long id;


    @Column(name = "skill_name", unique = true, nullable = false, length = 255)
    private String skillName;

    public Skill(String skillName) {
        this.skillName = skillName;
    }
}
