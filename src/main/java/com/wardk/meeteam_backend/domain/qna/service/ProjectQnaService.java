package com.wardk.meeteam_backend.domain.qna.service;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.qna.entity.ProjectQna;
import com.wardk.meeteam_backend.domain.qna.entity.QnaAnswer;
import com.wardk.meeteam_backend.domain.qna.repository.ProjectQnaRepository;
import com.wardk.meeteam_backend.domain.qna.repository.QnaAnswerRepository;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.web.qna.dto.response.ProjectQnaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 Q&A 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectQnaService {

    private final ProjectQnaRepository projectQnaRepository;
    private final QnaAnswerRepository qnaAnswerRepository;
    private final ProjectRepository projectRepository;
    private final MemberRepository memberRepository;

    /**
     * Q&A 목록 조회 (페이징)
     * @param viewerId 조회자 ID (null이면 비로그인 사용자)
     */
    @Transactional(readOnly = true)
    public Page<ProjectQnaResponse> getQnaList(Long projectId, Pageable pageable, Long viewerId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        Long leaderId = project.getCreator().getId();

        Page<ProjectQna> qnaPage = projectQnaRepository.findByProjectIdWithQuestioner(projectId, pageable);
        return qnaPage.map(qna -> ProjectQnaResponse.fromWithSecretMasking(qna, leaderId, viewerId));
    }

    /**
     * 질문 등록 (회원만 가능)
     */
    public ProjectQnaResponse createQuestion(Long projectId, Long memberId, String question, Boolean isSecret) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        Member questioner = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        ProjectQna qna = ProjectQna.create(project, questioner, question, isSecret);
        projectQnaRepository.save(qna);

        log.info("Q&A 질문 등록 - projectId: {}, questionerId: {}, isSecret: {}", projectId, memberId, isSecret);

        Long leaderId = project.getCreator().getId();
        return ProjectQnaResponse.from(qna, leaderId);
    }

    /**
     * 답변 등록 (질문자 본인 + 프로젝트 리더만 가능)
     */
    public ProjectQnaResponse addAnswer(Long projectId, Long qnaId, Long memberId, String answerContent) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectQna qna = projectQnaRepository.findById(qnaId)
                .orElseThrow(() -> new CustomException(ErrorCode.QNA_NOT_FOUND));

        Long leaderId = project.getCreator().getId();
        Long questionerId = qna.getQuestioner().getId();

        // 권한 확인: 질문자 본인 또는 프로젝트 리더만 답변 가능
        if (!memberId.equals(questionerId) && !memberId.equals(leaderId)) {
            throw new CustomException(ErrorCode.QNA_ANSWER_FORBIDDEN);
        }

        Member writer = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        QnaAnswer answer = QnaAnswer.create(qna, writer, answerContent);
        qnaAnswerRepository.save(answer);
        qna.addAnswer(answer);

        log.info("Q&A 답변 등록 - projectId: {}, qnaId: {}, writerId: {}, isLeader: {}",
                projectId, qnaId, memberId, memberId.equals(leaderId));

        return ProjectQnaResponse.from(qna, leaderId);
    }

    /**
     * 질문 삭제.
     * - 팀장: 모든 질문 삭제 가능
     * - 일반 사용자: 본인 질문만 삭제 가능 (단, 팀장 답변이 있으면 삭제 불가)
     */
    public void deleteQuestion(Long projectId, Long qnaId, Long memberId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectQna qna = projectQnaRepository.findById(qnaId)
                .orElseThrow(() -> new CustomException(ErrorCode.QNA_NOT_FOUND));

        Long leaderId = project.getCreator().getId();
        Long questionerId = qna.getQuestioner().getId();
        boolean isLeader = memberId.equals(leaderId);

        // 팀장은 모든 질문 삭제 가능
        if (isLeader) {
            projectQnaRepository.delete(qna);
            log.info("Q&A 질문 삭제 (팀장) - projectId: {}, qnaId: {}", projectId, qnaId);
            return;
        }

        // 본인 질문이 아니면 삭제 불가
        if (!memberId.equals(questionerId)) {
            throw new CustomException(ErrorCode.QNA_DELETE_FORBIDDEN);
        }

        // 팀장 답변이 있으면 삭제 불가
        boolean hasLeaderAnswer = qna.getAnswers().stream()
                .anyMatch(answer -> answer.getWriter().getId().equals(leaderId));

        if (hasLeaderAnswer) {
            throw new CustomException(ErrorCode.QNA_DELETE_HAS_LEADER_ANSWER);
        }

        projectQnaRepository.delete(qna);
        log.info("Q&A 질문 삭제 - projectId: {}, qnaId: {}, deleterId: {}", projectId, qnaId, memberId);
    }

    /**
     * 답변 삭제.
     * - 팀장: 모든 답변 삭제 가능
     * - 일반 사용자: 본인 답변만 삭제 가능
     */
    public void deleteAnswer(Long projectId, Long qnaId, Long answerId, Long memberId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectQna qna = projectQnaRepository.findById(qnaId)
                .orElseThrow(() -> new CustomException(ErrorCode.QNA_NOT_FOUND));

        QnaAnswer answer = qnaAnswerRepository.findById(answerId)
                .orElseThrow(() -> new CustomException(ErrorCode.QNA_ANSWER_NOT_FOUND));

        Long leaderId = project.getCreator().getId();
        boolean isLeader = memberId.equals(leaderId);

        // 팀장은 모든 답변 삭제 가능
        if (isLeader) {
            qnaAnswerRepository.delete(answer);
            log.info("Q&A 답변 삭제 (팀장) - projectId: {}, qnaId: {}, answerId: {}", projectId, qnaId, answerId);
            return;
        }

        // 본인 답변이 아니면 삭제 불가
        if (!memberId.equals(answer.getWriter().getId())) {
            throw new CustomException(ErrorCode.QNA_DELETE_FORBIDDEN);
        }

        qnaAnswerRepository.delete(answer);
        log.info("Q&A 답변 삭제 - projectId: {}, qnaId: {}, answerId: {}, deleterId: {}", projectId, qnaId, answerId, memberId);
    }
}
