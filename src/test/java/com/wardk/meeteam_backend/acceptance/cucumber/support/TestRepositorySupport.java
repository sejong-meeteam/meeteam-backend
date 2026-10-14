package com.wardk.meeteam_backend.acceptance.cucumber.support;

import com.wardk.meeteam_backend.domain.application.repository.ProjectApplicationRepository;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.domain.notification.repository.NotificationRepository;
import com.wardk.meeteam_backend.domain.project.repository.ProjectRepository;
import com.wardk.meeteam_backend.domain.projectmember.repository.ProjectMemberRepository;
import com.wardk.meeteam_backend.domain.recruitment.repository.RecruitmentStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Repository 통합 진입점 (Facade)
 */
@Component
public class TestRepositorySupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ProjectApplicationRepository projectApplicationRepository;

    @Autowired
    private RecruitmentStateRepository recruitmentStateRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    public MemberRepository member() {
        return memberRepository;
    }

    public ProjectRepository project() {
        return projectRepository;
    }

    public ProjectMemberRepository projectMember() {
        return projectMemberRepository;
    }

    public ProjectApplicationRepository projectApplication() {
        return projectApplicationRepository;
    }

    public RecruitmentStateRepository recruitmentState() {
        return recruitmentStateRepository;
    }

    public NotificationRepository notification() {
        return notificationRepository;
    }
}