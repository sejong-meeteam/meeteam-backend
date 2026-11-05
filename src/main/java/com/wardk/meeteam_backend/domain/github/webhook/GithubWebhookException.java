package com.wardk.meeteam_backend.domain.github.webhook;
public final class GithubWebhookException extends RuntimeException {
 private final int status;
 public GithubWebhookException(int status){super("GitHub webhook rejected");this.status=status;}
 public int status(){return status;}
}
