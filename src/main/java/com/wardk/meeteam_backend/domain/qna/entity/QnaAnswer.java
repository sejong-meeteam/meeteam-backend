package com.wardk.meeteam_backend.domain.qna.entity;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Q&A 답변(댓글) 엔티티.
 * 로그인한 회원 누구나 답변할 수 있습니다.
 */
@Entity
@Getter
@Table(name = "qna_answer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qna_id", nullable = false)
    private ProjectQna projectQna;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member writer;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private QnaAnswer(ProjectQna projectQna, Member writer, String content) {
        this.projectQna = projectQna;
        this.writer = writer;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public static QnaAnswer create(ProjectQna projectQna, Member writer, String content) {
        return new QnaAnswer(projectQna, writer, content);
    }
}
