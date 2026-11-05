package com.wardk.meeteam_backend.domain.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

/** Identity/name snapshots intentionally survive later member/project deletion. */
@Entity
@Table(name = "admin_audit_log", indexes = {
    @Index(name = "idx_admin_audit_target_created", columnList = "target_type,target_id,created_at"),
    @Index(name = "idx_admin_audit_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long adminId;
    @Column(nullable = false) private String adminName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AuditAction action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AuditTargetType targetType;
    @Column(nullable = false) private Long targetId;
    @Column(nullable = false) private String targetName;
    @Column(nullable = false, length = 200) private String reason;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "json") private Map<String, Object> metadata;
    @Column(nullable = false) private LocalDateTime createdAt;

    public AdminAuditLog(Long adminId, String adminName, AuditAction action, AuditTargetType targetType,
                         Long targetId, String targetName, String reason, Map<String, Object> metadata,
                         LocalDateTime createdAt) {
        this.adminId = adminId; this.adminName = adminName == null ? "(이름 없음)" : adminName;
        this.action = action; this.targetType = targetType; this.targetId = targetId;
        this.targetName = targetName == null ? "(이름 없음)" : targetName;
        this.reason = reason; this.metadata = metadata; this.createdAt = createdAt;
    }
}
