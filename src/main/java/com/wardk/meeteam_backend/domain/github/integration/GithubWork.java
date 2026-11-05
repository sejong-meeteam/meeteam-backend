package com.wardk.meeteam_backend.domain.github.integration;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="github_work", uniqueConstraints=@UniqueConstraint(name="uk_github_work_dedup", columnNames="dedup_key"))
@Getter @Setter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class GithubWork {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=24) private String kind;
    private Long projectId;
    private Long memberId;
    @Column(nullable=false) private long bindingVersion;
    @Lob @Column(columnDefinition="LONGTEXT",nullable=false) private String payload;
    @Column(name="dedup_key",length=190) private String dedupKey;
    @Column(nullable=false,length=16) private String status="PENDING";
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private Instant nextAttemptAt=Instant.now();
    private Instant lockedUntil;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(length=300) private String failureReason;
    public GithubWork(String kind,Long projectId,Long memberId,long bindingVersion,String payload,String dedupKey) {
        this.kind=kind;this.projectId=projectId;this.memberId=memberId;this.bindingVersion=bindingVersion;
        this.payload=payload==null?"{}":payload;this.dedupKey=dedupKey;
    }
}
