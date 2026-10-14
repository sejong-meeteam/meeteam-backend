package com.wardk.meeteam_backend.acceptance.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber 테스트 실행기
 *
 * 실행 방법:
 * - IDE: 이 클래스를 직접 실행
 * - Gradle: ./gradlew test --tests "*CucumberTest"
 * - 특정 태그만: @acceptance, @auth, @project 등
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.wardk.meeteam_backend.acceptance.cucumber")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, summary")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@acceptance")
public class CucumberTest {
}
