package com.wardk.meeteam_backend.external;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세종대 포털 로그인 테스트
 * 실제 테스트 시 @Disabled 제거 후 실행
 */
class SejongPortalLoginTest {

    private static final String PORTAL_LOGIN_URL = "https://portal.sejong.ac.kr";
    private static final String SSO_LOGIN_URL = "https://sso.sejong.ac.kr";

    /**
     * 세종대 포털 로그인 페이지 접근 테스트
     * - 로그인 페이지가 정상적으로 응답하는지 확인
     * - 필요한 쿠키와 폼 구조 파악
     */
    @Test
    @DisplayName("세종대 포털 로그인 페이지 접근 테스트")
    void testAccessLoginPage() {
        WebClient webClient = WebClient.builder()
                .baseUrl(PORTAL_LOGIN_URL)
                .build();

        ClientResponse response = webClient.get()
                .uri("/jsp/login/login.jsp")
                .exchange()
                .block();

        assertThat(response).isNotNull();
        System.out.println("=== 로그인 페이지 응답 ===");
        System.out.println("Status: " + response.statusCode());
        System.out.println("Headers: " + response.headers().asHttpHeaders());

        // 쿠키 확인
        List<String> cookies = response.headers().header(HttpHeaders.SET_COOKIE);
        System.out.println("Cookies: " + cookies);

        // 응답 본문 확인
        String body = response.bodyToMono(String.class).block();
        System.out.println("Body length: " + (body != null ? body.length() : 0));

        // form action, input field 이름 추출
        if (body != null) {
            // form action 찾기
            int formIndex = body.indexOf("<form");
            if (formIndex > -1) {
                int formEnd = body.indexOf(">", formIndex);
                System.out.println("Form tag: " + body.substring(formIndex, Math.min(formEnd + 1, body.length())));
            }

            // input field 찾기
            String[] inputs = body.split("<input");
            System.out.println("\n=== Input Fields ===");
            for (int i = 1; i < inputs.length && i < 20; i++) {
                int endTag = inputs[i].indexOf(">");
                if (endTag > -1) {
                    System.out.println("Input: <input" + inputs[i].substring(0, endTag + 1));
                }
            }
        }
    }

    /**
     * 세종대 SSO 서버 접근 테스트
     */
    @Test
    @DisplayName("세종대 SSO 서버 접근 테스트")
    void testAccessSsoServer() {
        WebClient webClient = WebClient.builder()
                .baseUrl(SSO_LOGIN_URL)
                .build();

        ClientResponse response = webClient.get()
                .uri("/")
                .exchange()
                .block();

        assertThat(response).isNotNull();
        System.out.println("=== SSO 서버 응답 ===");
        System.out.println("Status: " + response.statusCode());
        System.out.println("Headers: " + response.headers().asHttpHeaders());

        String body = response.bodyToMono(String.class).block();
        System.out.println("Body preview: " + (body != null ? body.substring(0, Math.min(500, body.length())) : "null"));
    }

    /**
     * 실제 로그인 POST 요청 테스트
     */
    @Test
    @DisplayName("세종대 포털 로그인 POST 테스트")
    void testLoginPost() {
        String userId = "21013220";
        String password = "19980611";

        WebClient webClient = WebClient.builder()
                .baseUrl(PORTAL_LOGIN_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();

        // Step 1: 로그인 페이지 접근하여 세션 쿠키 획득
        ClientResponse pageResponse = webClient.get()
                .uri("/jsp/login/loginSSL.jsp")
                .exchange()
                .block();

        List<String> cookies = pageResponse.headers().header(HttpHeaders.SET_COOKIE);
        String cookieHeader = cookies.stream()
                .map(c -> c.split(";")[0])
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
        System.out.println("세션 쿠키: " + cookieHeader);

        // Step 2: 로그인 폼 데이터 구성
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("id", userId);
        formData.add("password", password);
        formData.add("mainLogin", "Y");
        formData.add("rtUrl", "");

        // Step 3: 로그인 POST 요청 (세션 쿠키 포함)
        ClientResponse loginResponse = webClient.post()
                .uri("/jsp/login/login_action.jsp")
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.REFERER, PORTAL_LOGIN_URL + "/jsp/login/loginSSL.jsp")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .block();

        assertThat(loginResponse).isNotNull();
        System.out.println("\n=== 로그인 응답 ===");
        System.out.println("Status: " + loginResponse.statusCode());
        System.out.println("Headers: " + loginResponse.headers().asHttpHeaders());

        String responseBody = loginResponse.bodyToMono(String.class).block();
        System.out.println("Response body: " + responseBody);

        // 로그인 성공 여부 확인
        assertThat(responseBody).contains("var result = 'OK'");
        System.out.println("\n✅ 로그인 성공!");
    }

    /**
     * 세종대 SSO RTCas 방식 로그인 테스트
     * 일부 대학은 RTCas(Real-Time CAS) 방식 사용
     */
    @Test
    @Disabled("실제 계정 정보 필요")
    @DisplayName("세종대 RTCas SSO 로그인 테스트")
    void testRTCasLogin() {
        String userId = "YOUR_STUDENT_ID";
        String password = "YOUR_PASSWORD";

        WebClient webClient = WebClient.builder()
                .build();

        // RTCas 로그인 엔드포인트 시도
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("userId", userId);
        formData.add("password", password);
        formData.add("rtCas", "true");

        ClientResponse response = webClient.post()
                .uri("https://portal.sejong.ac.kr/jsp/login/loginSSL.jsp")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .block();

        assertThat(response).isNotNull();
        System.out.println("Status: " + response.statusCode());
        System.out.println("Headers: " + response.headers().asHttpHeaders());

        String body = response.bodyToMono(String.class).block();
        System.out.println("Body: " + body);
    }
}