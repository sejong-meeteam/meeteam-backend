package com.wardk.meeteam_backend.external;

import com.wardk.meeteam_backend.global.auth.client.SejongPortalClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SejongLoginApiTest {

    @Autowired
    private SejongPortalClient sejongPortalClient;

    @Test
    @DisplayName("세종대 포털 로그인 성공 테스트")
    void testSejongPortalLogin() {
        // given
        String studentId = "21013220";
        String password = "19980611";

        // when
        boolean result = sejongPortalClient.authenticate(studentId, password);

        // then
        assertThat(result).isTrue();
        System.out.println("✅ 세종대 포털 로그인 성공!");
    }
}