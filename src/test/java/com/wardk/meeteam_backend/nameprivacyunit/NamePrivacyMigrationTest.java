package com.wardk.meeteam_backend.nameprivacyunit;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated H2 compatibility/data-preservation check, not production MySQL verification. */
class NamePrivacyMigrationTest {
 @Test void additivePreferencePreservesIdentityAndDefaultsExistingRowsToPublic() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:name_privacy;MODE=MySQL", "sa", "")) {
   var s=c.createStatement();
   s.execute("CREATE TABLE member(member_id BIGINT PRIMARY KEY, real_name VARCHAR(255), department VARCHAR(200),school_profile_verified BIT,auth_version BIGINT)");
   s.execute("INSERT INTO member VALUES(7,'합성학생','테스트학과',TRUE,9)");
   ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V8__member_name_privacy.sql"));
   try(var r=s.executeQuery("SELECT real_name,department,school_profile_verified,auth_version,name_private FROM member WHERE member_id=7")){
    assertTrue(r.next());assertEquals("합성학생",r.getString(1));assertEquals("테스트학과",r.getString(2));assertTrue(r.getBoolean(3));assertEquals(9,r.getLong(4));assertFalse(r.getBoolean(5));
   }
   s.execute("UPDATE member SET name_private=TRUE WHERE member_id=7");
   try(var r=s.executeQuery("SELECT real_name,name_private FROM member WHERE member_id=7")){assertTrue(r.next());assertEquals("합성학생",r.getString(1));assertTrue(r.getBoolean(2));}
  }
 }
}
