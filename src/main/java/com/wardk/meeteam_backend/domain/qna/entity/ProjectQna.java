package com.wardk.meeteam_backend.domain.qna.entity;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 Q&A 엔티티.
 * 회원이 질문을 등록하고, 질문자 본인과 프로젝트 리더만 답변할 수 있습니다.
 */
@Entity
@Getter
@Table(name = "project_qna")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectQna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qna_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questioner_id", nullable = false)
    private Member questioner;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(name = "question_created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean isSecret = false;

    @OneToMany(mappedBy = "projectQna", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QnaAnswer> answers = new ArrayList<>();

    private ProjectQna(Project project, Member questioner, String question, Boolean isSecret) {
        this.project = project;
        this.questioner = questioner;
        this.question = question;
        this.createdAt = LocalDateTime.now();
        this.isSecret = isSecret != null ? isSecret : false;
    }

    public static ProjectQna create(Project project, Member questioner, String question, Boolean isSecret) {
        return new ProjectQna(project, questioner, question, isSecret);
    }

    public void addAnswer(QnaAnswer answer) {
        this.answers.add(answer);
    }
}
