package com.wardk.meeteam_backend.domain.github.collaboration;
import com.wardk.meeteam_backend.domain.github.integration.GithubWorkHandler;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor public class GithubRevokeWorkHandler implements GithubWorkHandler {
 private final GithubCollaborationService service;
 public String kind(){return "COLLAB_REVOKE";}
 public void handle(Job job){service.handleRevoke(job);}
}
