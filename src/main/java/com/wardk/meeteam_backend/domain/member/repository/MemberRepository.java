package com.wardk.meeteam_backend.domain.member.repository;

import com.wardk.meeteam_backend.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    void deleteByEmail(String email);

    Optional<Member> findOptionByEmail(String email);

    Optional<Member> findByEmail(String email);

    Boolean existsByEmail(String email);

    Optional<Member> findByStudentId(String studentId);

    Boolean existsByStudentId(String studentId);

    // ==================== 활성 회원 조회 (로그인용) ====================

    /**
     * 학번으로 활성 회원 조회 (탈퇴하지 않은 회원만)
     */
    Optional<Member> findByStudentIdAndDeletedAtIsNull(String studentId);

    /**
     * 이메일로 활성 회원 조회 (탈퇴하지 않은 회원만)
     */
    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    /**
     * 학번으로 활성 회원 존재 여부 확인 (탈퇴하지 않은 회원만)
     */
    Boolean existsByStudentIdAndDeletedAtIsNull(String studentId);
    /** Administrative removal blocks new portal registration, unlike ordinary self-withdrawal. */
    boolean existsByStudentIdAndAdminWithdrawnTrue(String studentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.studentId = :studentId order by m.id")
    java.util.List<Member> findAllByStudentIdForUpdate(@Param("studentId") String studentId);
}