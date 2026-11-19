package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic old rows in H2 MySQL mode, not a live MySQL or production migration check. */
class UtRound1MigrationTest {

    private static final String V9 = "db/migration/V9__ut_round1_project_job_application.sql";

    @Test
    void enumCategoryColumnKeepsExistingValuesAndAcceptsStudyAndContest() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:ut_round1_category;MODE=MySQL", "sa", "")) {
            Statement sql = connection.createStatement();
            createOldSchema(sql);
            sql.execute("INSERT INTO project(project_id, project_category) VALUES (1, 'CLUB')");
            assertThrows(Exception.class,
                    () -> sql.execute("INSERT INTO project(project_id, project_category) VALUES (2, 'STUDY')"));

            applyV9(connection);

            sql.execute("INSERT INTO project(project_id, project_category) VALUES (2, 'STUDY'), (3, 'CONTEST')");
            try (var rows = sql.executeQuery("SELECT project_category FROM project ORDER BY project_id")) {
                assertTrue(rows.next()); assertEquals("CLUB", rows.getString(1));
                assertTrue(rows.next()); assertEquals("STUDY", rows.getString(1));
                assertTrue(rows.next()); assertEquals("CONTEST", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void existingProjectsHaveNoCollaborationInfoAndNewValuesFit() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:ut_round1_collaboration;MODE=MySQL", "sa", "")) {
            Statement sql = connection.createStatement();
            createOldSchema(sql);
            sql.execute("INSERT INTO project(project_id, project_category) VALUES (1, 'CAPSTONE')");

            applyV9(connection);

            try (var row = sql.executeQuery("SELECT project_category, meeting_mode, weekly_hours, meeting_note FROM project")) {
                assertTrue(row.next());
                assertEquals("CAPSTONE", row.getString(1));
                assertNull(row.getString(2));
                assertNull(row.getObject(3));
                assertNull(row.getString(4));
            }
            sql.execute("UPDATE project SET meeting_mode='HYBRID', weekly_hours=40, meeting_note='" + "가".repeat(100) + "'");
            assertThrows(Exception.class, () -> sql.execute("UPDATE project SET meeting_note='" + "가".repeat(101) + "'"));
        }
    }

    @Test
    void decidedApplicationsAreBackfilledFromTheirLastEditAndOthersStayUndecided() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:ut_round1_decided;MODE=MySQL", "sa", "")) {
            Statement sql = connection.createStatement();
            createOldSchema(sql);
            sql.execute("INSERT INTO project_member_application VALUES "
                    + "(1, 'PENDING', '2026-10-01 10:00:00', '2026-10-01 10:00:00'), "
                    + "(2, 'ACCEPTED', '2026-10-01 10:00:00', '2026-10-03 09:30:00'), "
                    + "(3, 'REJECTED', '2026-10-02 10:00:00', '2026-10-04 18:00:00'), "
                    + "(4, 'CANCELLED', '2026-10-02 10:00:00', '2026-10-05 08:00:00')");

            applyV9(connection);

            try (var rows = sql.executeQuery("SELECT CAST(decided_at AS VARCHAR) FROM project_member_application ORDER BY project_application_id")) {
                assertTrue(rows.next()); assertNull(rows.getString(1));
                assertTrue(rows.next()); assertTrue(rows.getString(1).startsWith("2026-10-03 09:30:00"));
                assertTrue(rows.next()); assertTrue(rows.getString(1).startsWith("2026-10-04 18:00:00"));
                assertTrue(rows.next()); assertNull(rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    /** Mirrors the column types Hibernate generates on MySQL for the pre-V9 entities V9 touches. */
    static void createOldSchema(Statement sql) throws Exception {
        sql.execute("CREATE TABLE project (project_id BIGINT PRIMARY KEY, "
                + "project_category ENUM('CAPSTONE','CLUB','CREATIVE_SEMESTER','ETC'))");
        createJobTables(sql, oldEnum(JobFieldCode.values(), 6), oldEnum(JobPositionCode.values(), 21));
        sql.execute("CREATE TABLE project_member_application (project_application_id BIGINT PRIMARY KEY, "
                + "status ENUM('ACCEPTED','CANCELLED','PENDING','REJECTED') NOT NULL, "
                + "created_at DATETIME(6), edited_at DATETIME(6))");
    }

    /** The first {@code count} constants are the codes that existed before V9. */
    private static String oldEnum(Enum<?>[] codes, int count) {
        return Arrays.stream(codes).limit(count).map(c -> "'" + c.name() + "'")
                .collect(Collectors.joining(",", "ENUM(", ")"));
    }

    static void createJobTables(Statement sql, String fieldCodeType, String positionCodeType) throws Exception {
        sql.execute("CREATE TABLE job_field (job_field_id BIGINT AUTO_INCREMENT PRIMARY KEY, code " + fieldCodeType
                + " NOT NULL UNIQUE, name VARCHAR(100) NOT NULL)");
        sql.execute("CREATE TABLE job_position (job_position_id BIGINT AUTO_INCREMENT PRIMARY KEY, job_field_id BIGINT NOT NULL, "
                + "code " + positionCodeType + " NOT NULL UNIQUE, name VARCHAR(100) NOT NULL)");
        sql.execute("CREATE TABLE tech_stack (tech_stack_id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, "
                + "CONSTRAINT uk_tech_stack_name UNIQUE (name))");
        sql.execute("CREATE TABLE job_field_tech_stack (job_field_tech_stack_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "job_field_id BIGINT NOT NULL, tech_stack_id BIGINT NOT NULL, "
                + "CONSTRAINT uk_job_field_tech_stack_pair UNIQUE (job_field_id, tech_stack_id))");
    }

    static void applyV9(Connection connection) throws Exception {
        String mysql = new ClassPathResource(V9).getContentAsString(StandardCharsets.UTF_8);
        // H2 does not support MySQL's comma-separated ADD COLUMN clauses.
        // Adapt syntax only; real MySQL execution still requires separate verification.
        Pattern multiAdd = Pattern.compile("ALTER TABLE (\\w+)([^;]*?),\\s*ADD COLUMN");
        String h2 = mysql;
        for (Matcher m = multiAdd.matcher(h2); m.find(); m = multiAdd.matcher(h2)) {
            h2 = m.replaceFirst("ALTER TABLE $1$2; ALTER TABLE $1 ADD COLUMN");
        }
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(h2.getBytes(StandardCharsets.UTF_8)));
    }
}
