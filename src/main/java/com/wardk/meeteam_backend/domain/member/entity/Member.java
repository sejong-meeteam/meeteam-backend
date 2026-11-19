package com.wardk.meeteam_backend.domain.member.entity;

import com.wardk.meeteam_backend.domain.job.entity.JobPosition;
import com.wardk.meeteam_backend.domain.job.entity.TechStack;
import com.wardk.meeteam_backend.domain.projectmember.entity.ProjectMember;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongRegisterCommand;
import com.wardk.meeteam_backend.global.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_email_deleted", columnNames = {"email", "deleted_at"}),
        @UniqueConstraint(name = "uk_member_student_id_deleted", columnNames = {"student_id", "deleted_at"})
})
public class Member extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    private Integer age;

    private String password;

    private String realName;

    @Column(nullable = false)
    private boolean namePrivate = false;

    /** Safe default for every public/member-facing response. Never store a second name. */
    public String getPublicName() {
        if (!namePrivate || realName == null || realName.isBlank()) return realName;
        String value = realName.strip();
        int length = value.codePointCount(0, value.length());
        if (length <= 1) return "*";
        return value.substring(0, value.offsetByCodePoints(0, 1)) + "*".repeat(length - 1);
    }

    /** Null means an older client omitted the preference: preserve the stored value. */
    public void updateNamePrivacy(Boolean namePrivate) {
        if (namePrivate != null) this.namePrivate = namePrivate;
    }

    @Column(length = 200)
    private String department;

    @Column(nullable = false)
    private boolean schoolProfileVerified = false;

    private String storeFileName;

    @Enumerated(value = EnumType.STRING)
    private Gender gender;

    private LocalDate birth;

    private Boolean isParticipating;

    @Version
    private Long version;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 500)
    private List<MemberJobPosition> jobPositions = new ArrayList<>();

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 500)
    private List<MemberTechStack> memberTechStacks = new ArrayList<>();

    @OneToMany(mappedBy = "member")
    @BatchSize(size = 500)
    private List<ProjectMember> projectMembers = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private String provider;

    private String studentId;

    private String githubUrl;

    private String blogUrl;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private boolean suspended = false;

    private LocalDateTime suspendedUntil;

    @Column(length = 200)
    private String suspendReason;

    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private long authVersion = 0;

    @Column(nullable = false)
    private boolean adminWithdrawn = false;

    @Column(nullable = false)
    private long githubLinkVersion = 0;

    /** Invalidate pending GitHub linking attempts without logging the member out. */
    public void advanceGithubLinkVersion() {
        this.githubLinkVersion = Math.incrementExact(this.githubLinkVersion);
    }

    /** Revoke existing tokens whenever an administrator changes authorization. */
    public void changeRole(UserRole role) {
        this.role = java.util.Objects.requireNonNull(role);
        this.authVersion = Math.incrementExact(this.authVersion);
    }

    /** Preserve identity/history and role while permanently blocking self-service re-registration. */
    public void forceWithdraw() {
        this.adminWithdrawn = true;
        this.suspended = false;
        this.suspendedUntil = null;
        this.suspendReason = null;
        withdraw();
    }

    public boolean isSuspendedAt(LocalDateTime now) {
        return suspended && (suspendedUntil == null || now.isBefore(suspendedUntil));
    }

    public boolean isSuspended() {
        return isSuspendedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    public void suspend(Integer days, String reason, LocalDateTime now) {
        this.suspended = true;
        this.suspendedUntil = days == null ? null : now.plusDays(days);
        this.suspendReason = reason;
        this.authVersion = Math.incrementExact(this.authVersion);
    }

    public void unsuspend() {
        this.suspended = false;
        this.suspendedUntil = null;
        this.suspendReason = null;
        this.authVersion = Math.incrementExact(this.authVersion);
    }

    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    @Builder(access = AccessLevel.PRIVATE)
    private Member(String email, Integer age, String password, String realName,
                   String storeFileName, Gender gender, LocalDate birth,
                   Boolean isParticipating, UserRole role, String provider,
                   String studentId, String githubUrl, String blogUrl) {
        this.email = email;
        this.age = age;
        this.password = password;
        this.realName = realName;
        this.storeFileName = storeFileName;
        this.gender = gender;
        this.birth = birth;
        this.isParticipating = isParticipating;
        this.role = role;
        this.provider = provider;
        this.studentId = studentId;
        this.githubUrl = githubUrl;
        this.blogUrl = blogUrl;
    }

    /**
     * 세종대 포털 인증 회원가입용 정적 팩토리 메서드
     */
    public static Member createSejongMember(String studentId, SejongRegisterCommand command,
                                            String encodedPassword, String imageUrl) {
        String email = studentId + "@sju.ac.kr";
        return Member.builder()
                .email(email)
                .password(encodedPassword)
                .realName(command.name())
                .birth(command.birthDate())
                .gender(command.gender())
                .storeFileName(imageUrl)
                .isParticipating(true)
                .role(UserRole.USER)
                .provider("sejong")
                .studentId(studentId)
                .githubUrl(command.githubUrl())
                .blogUrl(command.blogUrl())
                .build();
    }

    /** School-authoritative identity; no client-entered name or department is trusted. */
    public static Member createSchoolVerifiedMember(String studentId, String name, String department,
                                                    SejongRegisterCommand command, String encodedPassword, String imageUrl) {
        var verified = new com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile(studentId, name, department);
        Member member = createSejongMember(verified.studentId(), command, encodedPassword, imageUrl);
        member.realName = verified.name();
        member.department = verified.department();
        member.schoolProfileVerified = true;
        return member;
    }

    /**
     * 테스트/시드 데이터용 정적 팩토리 메서드
     */
    public static Member createForTest(String email, String realName) {
        return Member.builder()
                .email(email)
                .realName(realName)
                .role(UserRole.USER)
                .build();
    }

    /**
     * 테스트용 일반 회원 생성 (비밀번호 포함)
     */
    public static Member createForTest(String email, String realName, String encodedPassword) {
        return Member.builder()
                .email(email)
                .realName(realName)
                .password(encodedPassword)
                .birth(java.time.LocalDate.of(1998, 3, 15))
                .gender(Gender.MALE)
                .isParticipating(true)
                .role(UserRole.USER)
                .build();
    }

    /** Operator identity, never produced by public student registration or the test-account factory. */
    public static Member createForLocalAdmin(String email, String encodedPassword) {
        return Member.builder()
                .email(email)
                .realName("운영 관리자")
                .password(encodedPassword)
                .role(UserRole.ADMIN)
                .provider("local-admin")
                .isParticipating(false)
                .build();
    }

    public boolean needsSchoolDepartment() {
        return studentId != null && (department == null || department.isBlank());
    }

    /** Supplement only missing school data; never rename or reclassify a legacy member. */
    public void supplementSchoolDepartment(com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile profile) {
        if (profile == null) return;
        if (!java.util.Objects.equals(studentId, profile.studentId())) {
            throw new com.wardk.meeteam_backend.global.exception.CustomException(
                    com.wardk.meeteam_backend.global.response.ErrorCode.SEJONG_PORTAL_ERROR);
        }
        if (needsSchoolDepartment()) this.department = profile.department();
    }

    /** Members without a verified school account never match anyone, including each other. */
    public boolean isSameUniversityAs(Member other) {
        Optional<University> mine = University.fromEmail(email);
        return mine.isPresent() && mine.equals(University.fromEmail(other.email));
    }

    public boolean isActive() {
        return !isDeleted() && !isSuspended();
    }

    public boolean isLocalAdminAccount() {
        return "local-admin".equals(provider);
    }

    public void validateProfileNameChange(String requestedName) {
        if (schoolProfileVerified) {
            if (requestedName != null && !java.util.Objects.equals(realName, requestedName)) {
                throw new com.wardk.meeteam_backend.global.exception.CustomException(
                        com.wardk.meeteam_backend.global.response.ErrorCode.INVALID_REQUEST,
                        "학교에서 확인한 이름은 직접 수정할 수 없습니다.");
            }
        } else if (requestedName == null || requestedName.isBlank()) {
            throw new com.wardk.meeteam_backend.global.exception.CustomException(
                    com.wardk.meeteam_backend.global.response.ErrorCode.INVALID_REQUEST, "이름을 입력해주세요.");
        }
    }

    public void addJobPosition(JobPosition jobPosition) {
        jobPositions.add(new MemberJobPosition(this, jobPosition));
    }

    public void addMemberTechStack(TechStack techStack, Integer displayOrder) {
        memberTechStacks.add(new MemberTechStack(this, techStack, displayOrder));
    }

    /**
     * 회원가입 시 직무 목록을 초기화합니다.
     */
    public void initializeJobPositions(List<JobPosition> jobPositions) {
        jobPositions.forEach(this::addJobPosition);
    }

    public void updateProfile(String realName, Integer age, Gender gender,
                              Boolean isParticipating, String introduction,
                              String githubUrl, String blogUrl) {

        validateProfileNameChange(realName);
        if (!schoolProfileVerified) this.realName = realName;
        this.age = age;
        this.gender = gender;
        this.isParticipating = isParticipating;
        this.introduction = introduction;
        this.githubUrl = githubUrl;
        this.blogUrl = blogUrl;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public void setStoreFileName(String storeFileName) {
        this.storeFileName = storeFileName;
    }

    /**
     * 회원 탈퇴 처리 (소프트 삭제)
     */
    public void withdraw() {
        this.deletedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.authVersion = Math.incrementExact(this.authVersion);
        this.isParticipating = false;
    }

    /**
     * 회원이 탈퇴했는지 확인
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * 나이를 반환합니다.
     * age 필드가 설정되어 있으면 해당 값을 반환하고,
     * 그렇지 않으면 birth 필드에서 계산합니다.
     */
    public Integer getAge() {
        if (this.age != null) {
            return this.age;
        }
        if (this.birth == null) {
            return null;
        }
        return Period.between(this.birth, LocalDate.now()).getYears();
    }
}
