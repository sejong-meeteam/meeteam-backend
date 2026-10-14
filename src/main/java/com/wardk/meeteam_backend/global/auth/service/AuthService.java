package com.wardk.meeteam_backend.global.auth.service;

import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.file.service.S3FileService;
import com.wardk.meeteam_backend.domain.job.entity.JobField;
import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import com.wardk.meeteam_backend.domain.job.repository.JobFieldRepository;
import com.wardk.meeteam_backend.domain.job.repository.JobPositionRepository;
import com.wardk.meeteam_backend.domain.job.repository.TechStackRepository;
import com.wardk.meeteam_backend.domain.member.entity.Member;
import com.wardk.meeteam_backend.domain.member.repository.MemberJobPositionRepository;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.domain.project.entity.Project;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectlike.repository.ProjectLikeRepository;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.qna.repository.ProjectQnaRepository;
import com.wardk.meeteam_backend.domain.qna.repository.QnaAnswerRepository;
import com.wardk.meeteam_backend.domain.skill.repository.MemberSkillRepository;
import com.wardk.meeteam_backend.global.auth.repository.SejongCodeRepository;
import com.wardk.meeteam_backend.global.auth.repository.TokenBlacklistRepository;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import com.wardk.meeteam_backend.global.auth.service.dto.MemberJobPositionCommand;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongLoginCommand;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongLoginResult;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongRegisterCommand;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongRegisterInfo;
import com.wardk.meeteam_backend.global.auth.service.dto.TechStackOrderCommand;
import com.wardk.meeteam_backend.global.auth.service.dto.TokenExchangeResult;
import com.wardk.meeteam_backend.web.auth.dto.register.RegisterDescriptionRequest;
import com.wardk.meeteam_backend.web.auth.dto.register.RegisterResponse;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 세종대 포털 인증 기반 인증 서비스.
 * OAuth2/자체 로그인은 제거되었으며, 세종대 포털 로그인만 지원합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final S3FileService s3FileService;
    private final JobFieldRepository jobFieldRepository;
    private final JobPositionRepository jobPositionRepository;
    private final TechStackRepository techStackRepository;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final SejongCodeRepository sejongCodeRepository;
    private final SejongPortalClient sejongPortalClient;

    // CASCADE 삭제용 Repository
    private final QnaAnswerRepository qnaAnswerRepository;
    private final ProjectQnaRepository projectQnaRepository;
    private final ProjectApplicationRepository projectApplicationRepository;
    private final ProjectLikeRepository projectLikeRepository;
    private final NotificationRepository notificationRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberSkillRepository memberSkillRepository;
    private final MemberJobPositionRepository memberJobPositionRepository;
    private final ProjectRepository projectRepository;

    /**
     * 세종대 포털 인증을 통한 로그인.
     * 기존 회원이면 토큰 발급, 신규 회원이면 회원가입용 코드 발급.
     *
     * @param command 세종대 로그인 커맨드 (학번, 비밀번호)
     * @return 로그인 결과 (기존 회원: 토큰, 신규 회원: 코드)
     */
    public SejongLoginResult sejongLogin(SejongLoginCommand command) {
        // 세종대 포털 인증
        sejongPortalClient.authenticate(command.studentId(), command.password());

        // 활성 회원 조회 (탈퇴하지 않은 회원만)
        return memberRepository.findByStudentIdAndDeletedAtIsNull(command.studentId())
                .map(member -> {
                    String accessToken = jwtUtil.createAccessToken(member);
                    String refreshToken = jwtUtil.createRefreshToken(member);
                    return SejongLoginResult.existingMember(accessToken, refreshToken);
                })
                .orElseGet(() -> {
                    // 신규 회원: 학번을 Redis에 저장하고 코드 반환
                    String code = sejongCodeRepository.saveRegisterInfo(
                            new SejongRegisterInfo(command.studentId())
                    );
                    return SejongLoginResult.newMember(code);
                });
    }

    /**
     * 세종대 포털 인증 후 회원가입.
     *
     * @param command 세종대 회원가입 커맨드 (코드 + 온보딩 정보)
     * @param file    프로필 이미지
     * @return 토큰 교환 결과
     */
    @Transactional
    public TokenExchangeResult sejongRegister(SejongRegisterCommand command, MultipartFile file) {
        // 코드로 학번 조회
        SejongRegisterInfo registerInfo = sejongCodeRepository.consumeRegisterInfo(command.code())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_SEJONG_CODE));

        // 학번 중복 검증
        validateStudentIdNotDuplicated(registerInfo.getStudentId());

        String imageUrl = uploadFile(file);

        // 회원 생성
        Member member = Member.createSejongMember(
                registerInfo.getStudentId(),
                command,
                bCryptPasswordEncoder.encode(UUID.randomUUID().toString()),
                imageUrl
        );

        // 직군/직무/기술스택 처리
        processAndAddJobPositions(member, command.jobPositions());

        memberRepository.save(member);

        // 토큰 발급
        String accessToken = jwtUtil.createAccessToken(member);
        String refreshToken = jwtUtil.createRefreshToken(member);
        return new TokenExchangeResult(accessToken, refreshToken);
    }

    private void validateStudentIdNotDuplicated(String studentId) {
        // 활성 회원 중 중복 검증 (탈퇴 회원은 재가입 가능)
        if (memberRepository.existsByStudentIdAndDeletedAtIsNull(studentId)) {
            throw new CustomException(ErrorCode.SEJONG_STUDENT_ID_ALREADY_EXISTS);
        }
    }

    /**
     * 직군/직무/기술스택 정보를 처리하여 Member에 추가합니다.
     */
    private void processAndAddJobPositions(Member member, List<MemberJobPositionCommand> commands) {
        for (MemberJobPositionCommand command : commands) {
            // 직군 조회 (ENUM 코드로)
            JobField jobField = jobFieldRepository.findByCode(command.jobFieldCode())
                    .orElseThrow(() -> new CustomException(ErrorCode.JOB_FIELD_NOT_FOUND));

            // 직무 포지션 조회 (ENUM 코드로)
            JobPosition jobPosition = jobPositionRepository.findByCode(command.jobPositionCode())
                    .orElseThrow(() -> new CustomException(ErrorCode.JOB_POSITION_NOT_FOUND));

            // 직무가 해당 직군에 속하는지 검증
            if (!jobPosition.getJobField().getId().equals(jobField.getId())) {
                throw new CustomException(ErrorCode.IS_NOT_ALLOWED_POSITION);
            }

            member.addJobPosition(jobPosition);

            // 기술스택 조회 (ID + displayOrder) - 직군 제약 없이 선택 가능
            if (command.techStacks() != null && !command.techStacks().isEmpty()) {
                for (TechStackOrderCommand techStackCommand : command.techStacks()) {
                    TechStack techStack = techStackRepository.findById(techStackCommand.id())
                            .orElseThrow(() -> new CustomException(ErrorCode.TECH_STACK_NOT_FOUND));

                    member.addMemberTechStack(techStack, techStackCommand.displayOrder());
                }
            }
        }
    }

    @Transactional
    public RegisterResponse registDesciption(Long memberId, RegisterDescriptionRequest introduction) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        member.setIntroduction(introduction.getIntroduce());

        return new RegisterResponse(member.getRealName(), member.getId());
    }

    /**
     * Refresh Token을 이용하여 새로운 Access Token을 발급받는 메서드.
     * Token Rotation 적용.
     */
    @Transactional
    public TokenExchangeResult refreshTokens(HttpServletRequest request) {
        // 쿠키에서 Refresh Token 추출
        String refreshToken = extractRefreshTokenFromCookies(request);

        if (refreshToken == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        // Refresh Token 유효성 검증
        try {
            // 만료 시간 검증
            if (jwtUtil.isExpired(refreshToken)) {
                throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
            }

            // 토큰 카테고리 확인 (refresh 토큰인지 확인)
            String category = jwtUtil.getCategory(refreshToken);
            if (!category.equals(JwtUtil.REFRESH_CATEGORY)) {
                throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            // 토큰에서 사용자 정보 추출
            String email = jwtUtil.getUsername(refreshToken);

            // DB에서 활성 사용자 존재 여부 확인 (탈퇴 회원 차단)
            Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                    .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_WITHDRAWN));

            // 새로운 Access Token 및 Refresh Token 생성 (Token Rotation)
            String newAccessToken = jwtUtil.createAccessToken(member);
            String newRefreshToken = jwtUtil.createRefreshToken(member);

            return new TokenExchangeResult(newAccessToken, newRefreshToken);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (Exception e) {
            if (e instanceof CustomException) {
                throw e;
            }
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private String extractRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (JwtUtil.REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String uploadFile(MultipartFile file) {
        String imageUrl = null;
        if (file != null && !file.isEmpty()) {
            imageUrl = s3FileService.uploadFile(file, "images");
        }
        return imageUrl;
    }

    /**
     * 회원 탈퇴 처리 (소프트 삭제).
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        member.withdraw();
    }

    /**
     * 회원 삭제 (하드 삭제).
     * 회원과 관련된 모든 데이터를 CASCADE 삭제합니다.
     */
    @Transactional
    public void deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 1. 회원이 작성한 Q&A 답변 삭제
        qnaAnswerRepository.deleteByWriterId(memberId);
        log.info("회원의 Q&A 답변 삭제 완료 - memberId: {}", memberId);

        // 2. 회원이 작성한 Q&A 질문 삭제 (답변도 cascade로 삭제됨)
        projectQnaRepository.deleteByQuestionerId(memberId);
        log.info("회원의 Q&A 질문 삭제 완료 - memberId: {}", memberId);

        // 3. 회원의 프로젝트 지원서 삭제
        projectApplicationRepository.deleteByApplicantId(memberId);
        log.info("회원의 지원서 삭제 완료 - memberId: {}", memberId);

        // 4. 회원의 프로젝트 좋아요 삭제
        projectLikeRepository.deleteByMemberId(memberId);
        log.info("회원의 좋아요 삭제 완료 - memberId: {}", memberId);

        // 5. 회원의 알림 삭제
        notificationRepository.deleteByReceiverId(memberId);
        log.info("회원의 알림 삭제 완료 - memberId: {}", memberId);

        // 6. 회원의 프로젝트 멤버십 삭제
        projectMemberRepository.deleteByMemberId(memberId);
        log.info("회원의 프로젝트 멤버십 삭제 완료 - memberId: {}", memberId);

        // 7. 회원이 생성한 프로젝트들 삭제
        List<Project> createdProjects = projectRepository.findByCreatorId(memberId);
        for (Project project : createdProjects) {
            projectQnaRepository.deleteByProjectId(project.getId());
            notificationRepository.deleteByProjectId(project.getId());
            projectRepository.delete(project);
        }
        log.info("회원이 생성한 프로젝트 삭제 완료 - memberId: {}, 프로젝트 수: {}", memberId, createdProjects.size());

        // 8. 회원의 기술스택 삭제
        memberSkillRepository.deleteByMemberId(memberId);
        log.info("회원의 기술스택 삭제 완료 - memberId: {}", memberId);

        // 9. 회원의 직무 포지션 삭제
        memberJobPositionRepository.deleteByMemberId(memberId);
        log.info("회원의 직무 포지션 삭제 완료 - memberId: {}", memberId);

        // 10. 회원 삭제
        memberRepository.delete(member);
        log.info("회원 하드 삭제 완료 - memberId: {}", memberId);
    }

    /**
     * 로그아웃 처리 - AccessToken을 블랙리스트에 추가합니다.
     */
    @Transactional
    public void logout(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                if (!jwtUtil.isExpired(accessToken)) {
                    String jti = jwtUtil.getJti(accessToken);
                    long remainingTime = jwtUtil.getAccessTokenExpirationTime(accessToken);

                    if (jti != null) {
                        tokenBlacklistRepository.addToBlacklist(jti, remainingTime);
                        log.info("AccessToken이 블랙리스트에 추가되었습니다. JTI: {}", jti);
                    } else {
                        log.warn("토큰에 JTI가 없습니다. 블랙리스트에 추가하지 않습니다.");
                    }
                }
            } catch (Exception e) {
                log.warn("토큰 블랙리스트 추가 중 오류 발생: {}", e.getMessage());
            }
        }
    }
}
