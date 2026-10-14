package com.wardk.meeteam_backend.acceptance.cucumber;

import com.wardk.meeteam_backend.acceptance.cucumber.support.DatabaseCleaner;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

/**
 * Cucumber Spring 통합 설정
 * 모든 Cucumber 시나리오에서 Spring Context를 공유합니다.
 */
@CucumberContextConfiguration
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

    @LocalServerPort
    private int port;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private DataSource dataSource;

    @Before
    public void setUp() {
        RestAssured.port = port;
        databaseCleaner.clear();

        // 스킬 초기 데이터 설정
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("data-h2.sql"));
        populator.execute(dataSource);

    }
}