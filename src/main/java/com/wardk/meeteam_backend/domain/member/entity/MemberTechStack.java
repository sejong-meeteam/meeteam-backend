package com.wardk.meeteam_backend.domain.member.entity;

import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "member_tech_stack")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTechStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_tech_stack_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tech_stack_id")
    private TechStack techStack;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public MemberTechStack(Member member, TechStack techStack, Integer displayOrder) {
        this.member = member;
        this.techStack = techStack;
        this.displayOrder = displayOrder;
    }
}
