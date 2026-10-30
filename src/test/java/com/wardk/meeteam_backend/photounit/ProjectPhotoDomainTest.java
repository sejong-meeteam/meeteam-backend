package com.wardk.meeteam_backend.photounit;

import com.wardk.meeteam_backend.domain.project.entity.Project;
import org.junit.jupiter.api.Test;

import static com.wardk.meeteam_backend.photounit.ProjectPhotoFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPhotoDomainTest {
    private void updateBasicInfo(Project project, String imageUrl) {
        var command = command(false);
        project.updateBasicInfo(command.name(), command.description(), command.projectCategory(),
                command.platformCategory(), command.githubRepositoryUrl(), command.communicationChannelUrl(),
                command.endDate(), command.recruitmentDeadlineType(), imageUrl);
    }

    @Test
    void nullImageOnBasicUpdateKeepsExistingImageWhileUpdatingOtherFields() {
        Project project = project(leader(), OLD_URL);
        updateBasicInfo(project, null);
        assertEquals(OLD_URL, project.getImageUrl());
        assertEquals("Edited project", project.getName());
        assertEquals("Edited description", project.getDescription());
    }

    @Test
    void explicitRemovalOnlyClearsImageAndIsIdempotent() {
        Project project = project(leader(), OLD_URL);
        var creator = project.getCreator();
        var recruitment = project.getRecruitments().get(0);
        project.removeImage();
        project.removeImage();
        assertNull(project.getImageUrl());
        assertEquals("Original project", project.getName());
        assertSame(creator, project.getCreator());
        assertSame(recruitment, project.getRecruitments().get(0));
    }

    @Test
    void initiallyNullImageIsValidForBasicUpdateAndExplicitRemoval() {
        Project project = project(leader(), null);
        updateBasicInfo(project, null);
        assertNull(project.getImageUrl());
        assertDoesNotThrow(project::removeImage);
        assertNull(project.getImageUrl());
    }

    @Test
    void nonNullBasicUpdateReplacesImageEvenAfterExplicitRemoval() {
        Project project = project(leader(), OLD_URL);
        project.removeImage();
        String replacement = BASE + "/images/new.png";
        updateBasicInfo(project, replacement);
        assertEquals(replacement, project.getImageUrl());
    }
}
