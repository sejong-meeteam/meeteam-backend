package com.wardk.meeteam_backend.acceptance.cucumber.support;

import com.wardk.meeteam_backend.acceptance.cucumber.api.AuthAPI;
import com.wardk.meeteam_backend.acceptance.cucumber.api.MemberAPI;
import com.wardk.meeteam_backend.acceptance.cucumber.api.NotificationApi;
import com.wardk.meeteam_backend.acceptance.cucumber.api.ProjectApplicationApi;
import com.wardk.meeteam_backend.acceptance.cucumber.api.ProjectCreateApi;
import com.wardk.meeteam_backend.acceptance.cucumber.api.ProjectLikeApi;
import com.wardk.meeteam_backend.acceptance.cucumber.api.ProjectManagementApi;
import com.wardk.meeteam_backend.acceptance.cucumber.api.ProjectSearchApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * API 클래스 통합 진입점 (Facade)
 */
@Component
public class TestApiSupport {

    @Autowired
    private AuthAPI authAPI;

    @Autowired
    private MemberAPI memberAPI;

    @Autowired
    private ProjectCreateApi projectCreateApi;

    @Autowired
    private ProjectLikeApi projectLikeApi;

    @Autowired
    private ProjectManagementApi projectManagementApi;

    @Autowired
    private ProjectSearchApi projectSearchApi;

    @Autowired
    private ProjectApplicationApi projectApplicationApi;

    @Autowired
    private NotificationApi notificationApi;

    public AuthAPI auth() {
        return authAPI;
    }

    public MemberAPI member() {
        return memberAPI;
    }

    public ProjectCreateApi projectCreate() {
        return projectCreateApi;
    }

    public ProjectLikeApi projectLike() {
        return projectLikeApi;
    }

    public ProjectManagementApi projectManagement() {
        return projectManagementApi;
    }

    public ProjectSearchApi projectSearch() {
        return projectSearchApi;
    }

    public ProjectApplicationApi projectApplication() {
        return projectApplicationApi;
    }

    public NotificationApi notification() {
        return notificationApi;
    }
}