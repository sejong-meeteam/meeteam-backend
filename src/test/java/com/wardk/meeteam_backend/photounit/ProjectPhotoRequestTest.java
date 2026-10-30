package com.wardk.meeteam_backend.photounit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.web.project.dto.request.ProjectEditRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectPhotoRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ProjectEditRequest read(String flag) throws Exception {
        return mapper.readValue("""
                {
                  "name":"Edited project", "description":"Edited description",
                  "projectCategory":"CAPSTONE", "platformCategory":"WEB",
                  "recruitmentDeadlineType":"RECRUITMENT_COMPLETED",
                  "leaderJobPositionCode":"JAVA_SPRING",
                  "recruitments":[{"recruitmentStateId":30,"jobFieldCode":"BACKEND",
                    "jobPositionCode":"JAVA_SPRING","recruitmentCount":2,"techStackIds":[40]}]
                %s
                }
                """.formatted(flag), ProjectEditRequest.class);
    }

    @Test
    void missingFlagKeepsLegacyRequestCompatible() throws Exception {
        ProjectEditRequest request = read("");
        assertNull(request.removeImage());
        assertFalse(request.toCommand().removeImage());
        assertFalse(request.toCommand().confirmDeletePositionsWithPendingApplicants());
        assertEquals(30L, request.toCommand().recruitments().get(0).recruitmentStateId());
    }

    @Test
    void explicitNullFlagMapsToFalse() throws Exception {
        ProjectEditRequest request = read(",\"removeImage\":null");
        assertNull(request.removeImage());
        assertFalse(request.toCommand().removeImage());
    }

    @Test
    void explicitFalseFlagMapsToFalse() throws Exception {
        ProjectEditRequest request = read(",\"removeImage\":false");
        assertEquals(Boolean.FALSE, request.removeImage());
        assertFalse(request.toCommand().removeImage());
    }

    @Test
    void explicitTrueFlagMapsToTrueWithoutChangingOtherFields() throws Exception {
        ProjectEditRequest request = read(",\"removeImage\":true");
        assertEquals(Boolean.TRUE, request.removeImage());
        assertTrue(request.toCommand().removeImage());
        assertEquals("Edited project", request.toCommand().name());
        assertEquals(1, request.toCommand().recruitments().size());
        assertFalse(request.toCommand().confirmDeletePositionsWithPendingApplicants());
    }
}
