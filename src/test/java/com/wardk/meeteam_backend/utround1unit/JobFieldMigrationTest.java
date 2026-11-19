package com.wardk.meeteam_backend.utround1unit;

import com.wardk.meeteam_backend.domain.job.entity.JobFieldCode;
import com.wardk.meeteam_backend.domain.job.entity.JobPositionCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic job rows in H2 MySQL mode, not a live MySQL or production migration check. */
class JobFieldMigrationTest {

    private static final Set<String> MARKETING_STACKS =
            Set.of("Google Analytics", "Meta Ads", "Google Ads", "Notion", "Figma", "Canva");
    private static final Set<String> DATA_STACKS = Set.of("Python", "R", "SQL", "Pandas", "Tableau", "Excel");

    @Test
    void migrationAddsMarketingAndDataAndReusesExistingTechStacks() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:ut_round1_jobs;MODE=MySQL", "sa", "")) {
            Statement sql = connection.createStatement();
            UtRound1MigrationTest.createOldSchema(sql);
            sql.execute("INSERT INTO job_field(code, name) VALUES ('PLANNING','기획'),('DESIGN','디자인'),"
                    + "('FRONTEND','프론트'),('BACKEND','백엔드'),('AI','AI'),('INFRA_OPERATION','인프라/운영')");
            sql.execute("INSERT INTO tech_stack(name) VALUES ('Notion'),('Figma'),('Google Analytics'),('Python'),('Pandas')");
            sql.execute("INSERT INTO job_field_tech_stack(job_field_id, tech_stack_id) SELECT jf.job_field_id, ts.tech_stack_id "
                    + "FROM job_field jf, tech_stack ts WHERE jf.code='PLANNING' AND ts.name IN ('Notion','Figma','Google Analytics')");
            long pythonId = scalar(sql, "SELECT tech_stack_id FROM tech_stack WHERE name='Python'");

            UtRound1MigrationTest.applyV9(connection);

            assertEquals(Set.of("MARKETING:마케팅", "DATA:데이터"),
                    rows(sql, "SELECT code || ':' || name FROM job_field WHERE code IN ('MARKETING','DATA')"));
            assertEquals(Set.of("PERFORMANCE_MARKETER:퍼포먼스 마케터", "CONTENT_MARKETER:콘텐츠 마케터", "BRAND_MARKETER:브랜드 마케터"),
                    positions(sql, "MARKETING"));
            assertEquals(Set.of("DATA_ANALYST:데이터 분석가", "DATA_ENGINEER:데이터 엔지니어", "DATA_SCIENTIST:데이터 사이언티스트"),
                    positions(sql, "DATA"));
            assertEquals(MARKETING_STACKS, stacks(sql, "MARKETING"));
            assertEquals(DATA_STACKS, stacks(sql, "DATA"));
            assertEquals(Set.of("Notion", "Figma", "Google Analytics"), stacks(sql, "PLANNING"));
            assertEquals(pythonId, scalar(sql, "SELECT tech_stack_id FROM tech_stack WHERE name='Python'"));
            assertEquals(12, scalar(sql, "SELECT COUNT(*) FROM tech_stack"));
        }
    }

    @Test
    void seedMatchesTheJobEnumsAndTheMigrationMappings() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:ut_round1_seed;MODE=MySQL", "sa", "")) {
            Statement sql = connection.createStatement();
            UtRound1MigrationTest.createJobTables(sql, "VARCHAR(50)", "VARCHAR(70)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed/job-seed.sql"));

            assertEquals(Arrays.stream(JobFieldCode.values()).map(c -> c.name() + ":" + c.getDisplayName())
                            .collect(Collectors.toSet()),
                    rows(sql, "SELECT code || ':' || name FROM job_field"));
            assertEquals(Arrays.stream(JobPositionCode.values())
                            .map(c -> c.getJobFieldCode() + ":" + c.name() + ":" + c.getDisplayName())
                            .collect(Collectors.toSet()),
                    rows(sql, "SELECT jf.code || ':' || jp.code || ':' || jp.name FROM job_position jp "
                            + "JOIN job_field jf ON jf.job_field_id = jp.job_field_id"));
            assertEquals(MARKETING_STACKS, stacks(sql, "MARKETING"));
            assertEquals(DATA_STACKS, stacks(sql, "DATA"));
            assertEquals(82, scalar(sql, "SELECT COUNT(*) FROM tech_stack"));
        }
    }

    private static Set<String> positions(Statement sql, String field) throws Exception {
        return rows(sql, "SELECT jp.code || ':' || jp.name FROM job_position jp JOIN job_field jf "
                + "ON jf.job_field_id = jp.job_field_id WHERE jf.code = '" + field + "'");
    }

    private static Set<String> stacks(Statement sql, String field) throws Exception {
        return rows(sql, "SELECT ts.name FROM job_field_tech_stack m JOIN job_field jf ON jf.job_field_id = m.job_field_id "
                + "JOIN tech_stack ts ON ts.tech_stack_id = m.tech_stack_id WHERE jf.code = '" + field + "'");
    }

    private static Set<String> rows(Statement sql, String query) throws Exception {
        Set<String> values = new HashSet<>();
        try (var rows = sql.executeQuery(query)) {
            while (rows.next()) values.add(rows.getString(1));
        }
        return values;
    }

    private static long scalar(Statement sql, String query) throws Exception {
        try (var row = sql.executeQuery(query)) {
            assertTrue(row.next());
            return row.getLong(1);
        }
    }
}
