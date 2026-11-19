package com.wardk.meeteam_backend.utround1unit;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

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

    /** Mirrors the column types Hibernate generates on MySQL for the pre-V9 entities. */
    static void createOldSchema(Statement sql) throws Exception {
        sql.execute("CREATE TABLE project (project_id BIGINT PRIMARY KEY, "
                + "project_category ENUM('CAPSTONE','CLUB','CREATIVE_SEMESTER','ETC'))");
    }

    static void applyV9(Connection connection) throws Exception {
        String mysql = new ClassPathResource(V9).getContentAsString(StandardCharsets.UTF_8);
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(mysql.getBytes(StandardCharsets.UTF_8)));
    }
}
