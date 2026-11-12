package com.wardk.meeteam_backend.schoolregistrationunit;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic old rows in H2 MySQL mode, not a live MySQL or production migration check. */
class SchoolProfileMigrationTest {
    @Test
    void additiveMigrationKeepsExistingMemberIdentityAndUsesSafeDefaults() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:h2:mem:school_profile_migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            var sql=connection.createStatement();
            sql.execute("CREATE TABLE member (member_id BIGINT PRIMARY KEY, real_name VARCHAR(255), email VARCHAR(255), auth_version BIGINT)");
            sql.execute("INSERT INTO member VALUES (1, '기존 학생', 'synthetic@example.invalid', 7)");
            // H2 does not support MySQL's comma-separated ADD COLUMN clauses.
            // Adapt syntax only; real MySQL execution still requires separate verification.
            String mysql = new ClassPathResource("db/migration/V7__school_verified_member_profile.sql")
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            String h2 = mysql.replaceAll(",\\s*ADD COLUMN", "; ALTER TABLE member ADD COLUMN");
            ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.ByteArrayResource(
                    h2.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            try(var row=sql.executeQuery("SELECT member_id,real_name,email,auth_version,department,school_profile_verified FROM member")) {
                assertTrue(row.next());
                assertEquals(1,row.getLong(1));
                assertEquals("기존 학생",row.getString(2));
                assertEquals("synthetic@example.invalid",row.getString(3));
                assertEquals(7,row.getLong(4));
                assertNull(row.getString(5));
                assertFalse(row.getBoolean(6));
                assertFalse(row.next());
            }
            sql.execute("UPDATE member SET department='테스트학과',school_profile_verified=TRUE WHERE member_id=1");
            try(var row=sql.executeQuery("SELECT department,school_profile_verified FROM member WHERE member_id=1")) {
                assertTrue(row.next());assertEquals("테스트학과",row.getString(1));assertTrue(row.getBoolean(2));
            }
        }
    }
}
