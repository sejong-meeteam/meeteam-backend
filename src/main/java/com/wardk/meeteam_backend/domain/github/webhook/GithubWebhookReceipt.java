package com.wardk.meeteam_backend.domain.github.webhook;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="github_webhook_receipt")
public class GithubWebhookReceipt {
 @Id private Long workId;
 @Column(nullable=false) private Instant processedAt;
 protected GithubWebhookReceipt(){}
 public GithubWebhookReceipt(long workId){this.workId=workId;this.processedAt=Instant.now();}
}
