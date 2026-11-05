package com.wardk.meeteam_backend.adminvisibilityunit;

import com.wardk.meeteam_backend.domain.project.entity.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectVisibilityTest {
    @Test void defaultsAndLegacyDeletedBuilderRemainCompatible() {
        Project project = Project.builder().build();
        assertThat(project.getVisibility()).isEqualTo(ProjectVisibility.VISIBLE);
        assertThat(project.isPubliclyVisible()).isTrue();
        assertThat(project.isDeleted()).isFalse();
        Project legacy = Project.builder().isDeleted(true).build();
        assertThat(legacy.getVisibility()).isEqualTo(ProjectVisibility.DELETED);
        assertThat(legacy.isPubliclyVisible()).isFalse();
        assertThat(legacy.isDeleted()).isTrue();
    }

    @Test void hideDeleteAndRestoreNeverChangeRecruitment() {
        for (Recruitment recruitment : Recruitment.values()) {
            Project project = Project.builder().recruitmentStatus(recruitment).build();
            project.changeVisibility(ProjectVisibility.HIDDEN);
            assertThat(project.isPubliclyVisible()).isFalse();
            assertThat(project.isDeleted()).isFalse();
            assertThat(project.getRecruitmentStatus()).isEqualTo(recruitment);
            project.delete();
            assertThat(project.getVisibility()).isEqualTo(ProjectVisibility.DELETED);
            assertThat(project.isDeleted()).isTrue();
            assertThat(project.getRecruitmentStatus()).isEqualTo(recruitment);
            project.changeVisibility(ProjectVisibility.HIDDEN);
            assertThat(project.isDeleted()).isFalse();
            project.changeVisibility(ProjectVisibility.VISIBLE);
            assertThat(project.isPubliclyVisible()).isTrue();
            assertThat(project.isDeleted()).isFalse();
            assertThat(project.getRecruitmentStatus()).isEqualTo(recruitment);
        }
    }

    @Test void rejectsNullWithoutChangingExistingState() {
        Project project = Project.builder().isDeleted(true).build();
        assertThatNullPointerException().isThrownBy(() -> project.changeVisibility(null));
        assertThat(project.getVisibility()).isEqualTo(ProjectVisibility.DELETED);
        assertThat(project.isDeleted()).isTrue();
    }
}
