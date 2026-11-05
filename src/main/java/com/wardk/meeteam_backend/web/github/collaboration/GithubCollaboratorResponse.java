package com.wardk.meeteam_backend.web.github.collaboration;
import java.time.OffsetDateTime;
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record GithubCollaboratorResponse(Long memberId,String name,String profileImageUrl,boolean isLeader,String githubLogin,String accessStatus,OffsetDateTime invitedAt) {}
