package com.wardk.meeteam_backend.adminloginunit;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.wardk.meeteam_backend.domain.member.entity.*;
import com.wardk.meeteam_backend.domain.member.repository.MemberRepository;
import com.wardk.meeteam_backend.global.auth.service.AdminAccountService;
import com.wardk.meeteam_backend.global.util.JwtUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Hibernate/H2 persistence only; not a MySQL multi-instance bootstrap race test. */
@DataJpaTest(excludeAutoConfiguration=org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class, properties={
 "spring.datasource.url=jdbc:h2:mem:admin-local-login;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
 "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
 "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@ContextConfiguration(classes=AdminAccountPersistenceTest.Config.class)
class AdminAccountPersistenceTest {
 @Configuration @EntityScan("com.wardk.meeteam_backend.domain")
 @EnableJpaRepositories(basePackageClasses=MemberRepository.class)
 static class Config {
  @Bean EntityManager entityManager(jakarta.persistence.EntityManagerFactory factory) {
   return org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory);
  }
  @Bean JPAQueryFactory queryFactory(EntityManager em) { return new JPAQueryFactory(em); }
 }
 @Autowired EntityManager em;
 @Autowired MemberRepository members;
 @Test void bootstrapPersistsOnceAndExistingAccountAuthenticatesAfterReload() {
  String syntheticPassword="synthetic-bootstrap-only";
  var encoder=new BCryptPasswordEncoder(4); var jwt=mock(JwtUtil.class);
  var service=new AdminAccountService(members,encoder,jwt,"99123456",syntheticPassword);
  service.run(null); em.flush(); em.clear();
  var created=members.findByEmail(AdminAccountService.EMAIL).orElseThrow();
  Long id=created.getId(); String originalHash=created.getPassword();
  assertThat(created.getRole()).isEqualTo(UserRole.ADMIN);
  assertThat(created.getStudentId()).isNull();
  assertThat(encoder.matches(syntheticPassword,originalHash)).isTrue();
  new AdminAccountService(members,encoder,jwt,"99123456",syntheticPassword).run(null);
  em.flush(); em.clear();
  assertThat(members.count()).isEqualTo(1);
  var reloaded=members.findById(id).orElseThrow();
  assertThat(reloaded.getPassword()).isEqualTo(originalHash);
  assertThat(reloaded.getAuthVersion()).isZero();
  service.login("99123456",syntheticPassword); em.flush();
  assertThat(reloaded.getLastLoginAt()).isNotNull();
  verify(jwt).createAccessToken(reloaded); verify(jwt).createRefreshToken(reloaded);
 }
 @Test void publicSearchAndMainHideOperatorEvenAfterRoleChangeAndKeepNullProviderStudents() {
  var ordinary=Member.createForTest("ordinary@example.invalid","Ordinary");
  var student=Member.createForTest("student@example.invalid","Student");
  ReflectionTestUtils.setField(student,"provider","sejong");
  ReflectionTestUtils.setField(student,"studentId","20261234");
  var operator=Member.createForLocalAdmin(AdminAccountService.EMAIL,"synthetic-hash");
  var demoted=Member.createForLocalAdmin("demoted@example.invalid","synthetic-hash");
  ReflectionTestUtils.setField(demoted,"role",UserRole.USER);
  members.saveAll(List.of(ordinary,student,operator,demoted)); em.flush(); em.clear();
  for(var sort:List.of(Sort.unsorted(),Sort.by(Sort.Direction.DESC,"projectCount"))) {
   var page=PageRequest.of(0,10,sort);
   assertThat(members.findAllWithProjectCountSort(page).getContent()).extracting(Member::getEmail)
      .containsExactlyInAnyOrder("ordinary@example.invalid","student@example.invalid");
   assertThat(members.searchMembersV1(null,null,List.of(),page).getContent()).extracting(Member::getEmail)
      .containsExactlyInAnyOrder("ordinary@example.invalid","student@example.invalid");
  }
 }
}
