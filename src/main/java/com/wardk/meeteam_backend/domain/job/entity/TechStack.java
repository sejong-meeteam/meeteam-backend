package com.wardk.meeteam_backend.domain.job.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "tech_stack",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tech_stack_name", columnNames = "name")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tech_stack_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    private TechStack(String name) {
        this.name = name;
    }

    public static TechStack of(String name) {
        return new TechStack(name);
    }
}
