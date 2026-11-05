package com.wardk.meeteam_backend.domain.github.activity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="github_activity_event",uniqueConstraints=@UniqueConstraint(name="uk_github_event_dedup",columnNames="dedup_key"),indexes=@Index(name="ix_github_event_repository_time",columnList="repository_id,occurred_at")) @Getter @Setter @NoArgsConstructor
public class GithubActivityEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="repository_id",nullable=false) private long repositoryId;
 @Column(name="dedup_key",length=190,nullable=false) private String dedupKey;
 @Column(nullable=false,length=32) private String type;
 private Long githubUserId;
 private Integer number;
 @Column(length=1024) private String title;
 @Column(length=1000) private String url;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
}
