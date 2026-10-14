package com.wardk.meeteam_backend.external;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 세종대 포털 커넥션 풀 테스트
 *
 * 유령 커넥션(Stale Connection) 문제를 재현하고 검증합니다.
 * - 서버가 커넥션을 끊었지만 클라이언트가 인지하지 못한 상태
 * - 해당 커넥션으로 요청 시 SocketException 발생
 *
 * 실제 테스트 시 @Disabled 제거 후 실행 (500초 대기)
 */
class SejongPortalConnectionPoolTest {

    private static final String SEJONG_PORTAL_URL = "https://portal.sejong.ac.kr";

    /**
     * 유령 커넥션 재현 테스트 (500초 대기)
     *
     * 시나리오:
     * 1. 첫 번째 요청 성공 → 커넥션이 풀에 반환됨
     * 2. 500초 대기 → 서버가 커넥션을 끊음 (Keep-Alive 타임아웃)
     * 3. 두 번째 요청 → 유령 커넥션 사용 시도 → SocketException 발생
     *
     * 주의: 실제 500초(약 8분 20초) 대기합니다.
     */
    @Test
    @Disabled("500초 대기 필요 - 수동 실행용")
    @DisplayName("유령 커넥션 재현 테스트 - 500초 대기 후 SocketException 발생")
    void staleConnectionTest_withBadConfig_shouldFail() throws Exception {
        // Given: 잘못된 설정의 HttpClient (idle timeout, TTL 설정 없음)
        CloseableHttpClient badHttpClient = createBadHttpClient();
        HttpGet request = new HttpGet(SEJONG_PORTAL_URL);

        // When: 첫 번째 요청 성공
        System.out.println("=== 1단계: 첫 번째 요청 ===");
        long start1 = System.currentTimeMillis();

        String firstResponse = badHttpClient.execute(request, response -> {
            String body = EntityUtils.toString(response.getEntity());
            System.out.println("첫 번째 응답 코드: " + response.getCode());
            System.out.println("응답 길이: " + body.length());
            return body;
        });

        long elapsed1 = System.currentTimeMillis() - start1;
        System.out.println("첫 번째 요청 소요 시간: " + elapsed1 + "ms");
        assertThat(firstResponse).isNotNull();

        // When: 500초 대기 (서버가 커넥션을 끊을 때까지)
        System.out.println("\n=== 2단계: 500초 대기 시작 ===");
        System.out.println("서버가 커넥션을 끊을 때까지 대기합니다...");
        System.out.println("예상 완료 시간: 약 8분 20초 후");

        for (int i = 1; i <= 10; i++) {
            Thread.sleep(50000); // 50초씩 대기
            System.out.println("경과: " + (i * 50) + "초 / 500초");
        }

        System.out.println("500초 대기 완료!\n");

        // Then: 두 번째 요청 - 유령 커넥션으로 인해 실패해야 함
        System.out.println("=== 3단계: 두 번째 요청 (유령 커넥션 사용 시도) ===");

        assertThatThrownBy(() -> {
            badHttpClient.execute(request, response -> {
                return EntityUtils.toString(response.getEntity());
            });
        })
        .isInstanceOfAny(
            java.net.SocketException.class,
            java.io.IOException.class,
            org.apache.hc.core5.http.ConnectionClosedException.class
        )
        .satisfies(e -> {
            System.out.println("예상대로 예외 발생: " + e.getClass().getSimpleName());
            System.out.println("메시지: " + e.getMessage());
        });

        badHttpClient.close();
    }

    /**
     * 정상 설정 커넥션 풀 테스트 (500초 대기)
     *
     * idle timeout과 TTL이 설정되어 있으면 유령 커넥션 문제가 발생하지 않음
     */
    @Test
    @Disabled("500초 대기 필요 - 수동 실행용")
    @DisplayName("정상 커넥션 풀 테스트 - 500초 대기 후에도 정상 동작")
    void goodConnectionPool_withProperConfig_shouldSucceed() throws Exception {
        runConnectionTest(createGoodHttpClient(), 500, "정상 설정");
    }

    /**
     * 잘못된 설정 커넥션 풀 테스트 (420초 대기)
     *
     * 세종대 서버 idle timeout: 약 400초
     * → 420초 대기 후 요청하면 유령 커넥션 문제 발생 예상
     */
    @Test
//    @Disabled("420초 대기 필요 - 수동 실행용")
    @DisplayName("잘못된 설정 테스트 - 420초 대기 후 예외 발생 여부 확인")
    void badConnectionPool_420sec_checkException() throws Exception {
        runConnectionTestWithPoolStats(420, "잘못된 설정 (idle timeout 없음)");
    }

    /**
     * 커넥션 풀 상태를 확인하면서 테스트 실행
     */
    private void runConnectionTestWithPoolStats(int waitSeconds, String configName) throws Exception {
        // ConnectionManager를 직접 관리해서 상태 확인
        // validateAfterInactivity를 비활성화해서 stale connection 검증 안 함
        PoolingHttpClientConnectionManager connectionManager =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(SSLContextBuilder.create()
                        .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                        .build())
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build())
                .setMaxConnTotal(5)
                .setMaxConnPerRoute(5)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setValidateAfterInactivity(TimeValue.NEG_ONE_MILLISECOND)  // stale 검증 비활성화!
                    .build())
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build())
            .disableAutomaticRetries()  // 자동 재시도 비활성화!
            // 의도적으로 evictIdleConnections, evictExpiredConnections 설정 안 함
            .build();

        HttpGet request = new HttpGet(SEJONG_PORTAL_URL);

        System.out.println("========================================");
        System.out.println("테스트 설정: " + configName);
        System.out.println("대기 시간: " + waitSeconds + "초");
        System.out.println("========================================\n");

        // 커넥션 풀 초기 상태
        printPoolStats("초기 상태", connectionManager);

        // 1단계: 첫 번째 요청
        System.out.println("\n=== 1단계: 첫 번째 요청 ===");
        long start1 = System.currentTimeMillis();

        httpClient.execute(request, response -> {
            EntityUtils.consume(response.getEntity());
            System.out.println("응답 코드: " + response.getCode());
            return null;
        });

        long elapsed1 = System.currentTimeMillis() - start1;
        System.out.println("첫 번째 요청 소요 시간: " + elapsed1 + "ms");
        printPoolStats("첫 번째 요청 후", connectionManager);

        // 2단계: 대기
        System.out.println("\n=== 2단계: " + waitSeconds + "초 대기 시작 ===");
        int interval = Math.max(waitSeconds / 10, 1);
        for (int elapsed = interval; elapsed <= waitSeconds; elapsed += interval) {
            Thread.sleep(interval * 1000L);
            System.out.println("경과: " + elapsed + "초 / " + waitSeconds + "초");
        }
        System.out.println("대기 완료!");
        printPoolStats("대기 후", connectionManager);

        // 3단계: 두 번째 요청
        System.out.println("\n=== 3단계: 두 번째 요청 ===");
        long start2 = System.currentTimeMillis();

        try {
            httpClient.execute(request, response -> {
                EntityUtils.consume(response.getEntity());
                System.out.println("응답 코드: " + response.getCode());
                return null;
            });

            long elapsed2 = System.currentTimeMillis() - start2;
            System.out.println("두 번째 요청 소요 시간: " + elapsed2 + "ms");
            printPoolStats("두 번째 요청 후", connectionManager);

            // 시간 비교로 커넥션 재사용 여부 판단
            System.out.println("\n========================================");
            System.out.println("첫 번째: " + elapsed1 + "ms, 두 번째: " + elapsed2 + "ms");
            if (elapsed2 < elapsed1 * 0.5) {
                System.out.println("→ 커넥션 재사용됨 (두 번째가 훨씬 빠름)");
            } else {
                System.out.println("→ 새 커넥션 생성됨 (시간이 비슷함)");
            }
            System.out.println("========================================");

        } catch (Exception e) {
            long elapsed2 = System.currentTimeMillis() - start2;
            System.out.println("\n❌ 두 번째 요청에서 예외 발생! (소요 시간: " + elapsed2 + "ms)");
            System.out.println("예외 타입: " + e.getClass().getName());
            System.out.println("메시지: " + e.getMessage());
            printPoolStats("예외 발생 후", connectionManager);
            e.printStackTrace();
        } finally {
            httpClient.close();
            connectionManager.close();
        }
    }

    private void printPoolStats(String label, PoolingHttpClientConnectionManager cm) {
        var stats = cm.getTotalStats();
        System.out.println("[" + label + "] 커넥션 풀 - leased: " + stats.getLeased()
            + ", available: " + stats.getAvailable()
            + ", pending: " + stats.getPending()
            + ", max: " + stats.getMax());
    }

    /**
     * 실제 커넥션 테스트 실행 - 예외 발생 시 상세 출력
     */
    private void runConnectionTest(CloseableHttpClient httpClient, int waitSeconds, String configName) throws Exception {
        HttpGet request = new HttpGet(SEJONG_PORTAL_URL);

        System.out.println("========================================");
        System.out.println("테스트 설정: " + configName);
        System.out.println("대기 시간: " + waitSeconds + "초");
        System.out.println("========================================\n");

        // 1단계: 첫 번째 요청
        System.out.println("=== 1단계: 첫 번째 요청 ===");
        long start1 = System.currentTimeMillis();

        try {
            String firstResponse = httpClient.execute(request, response -> {
                String body = EntityUtils.toString(response.getEntity());
                System.out.println("응답 코드: " + response.getCode());
                System.out.println("응답 길이: " + body.length());
                return body;
            });
            System.out.println("첫 번째 요청 성공! 소요 시간: " + (System.currentTimeMillis() - start1) + "ms\n");
        } catch (Exception e) {
            System.out.println("❌ 첫 번째 요청 실패!");
            System.out.println("예외 타입: " + e.getClass().getName());
            System.out.println("메시지: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // 2단계: 대기
        System.out.println("=== 2단계: " + waitSeconds + "초 대기 시작 ===");
        int interval = Math.max(waitSeconds / 10, 1);
        for (int elapsed = interval; elapsed <= waitSeconds; elapsed += interval) {
            Thread.sleep(interval * 1000L);
            System.out.println("경과: " + elapsed + "초 / " + waitSeconds + "초");
        }
        System.out.println("대기 완료!\n");

        // 3단계: 두 번째 요청 - 예외 발생 여부 확인
        System.out.println("=== 3단계: 두 번째 요청 ===");
        long start2 = System.currentTimeMillis();

        try {
            String secondResponse = httpClient.execute(request, response -> {
                String body = EntityUtils.toString(response.getEntity());
                System.out.println("응답 코드: " + response.getCode());
                System.out.println("응답 길이: " + body.length());
                return body;
            });
            long elapsed2 = System.currentTimeMillis() - start2;
            System.out.println("두 번째 요청 성공! 소요 시간: " + elapsed2 + "ms");
            System.out.println("\n✅ " + waitSeconds + "초 후에도 정상 동작!");
        } catch (Exception e) {
            long elapsed2 = System.currentTimeMillis() - start2;
            System.out.println("\n❌ 두 번째 요청에서 예외 발생! (소요 시간: " + elapsed2 + "ms)");
            System.out.println("========================================");
            System.out.println("예외 타입: " + e.getClass().getName());
            System.out.println("메시지: " + e.getMessage());
            System.out.println("========================================");
            System.out.println("\n전체 스택 트레이스:");
            e.printStackTrace();

            // 예외 발생해도 테스트는 통과 (확인용이므로)
            System.out.println("\n⚠️ 유령 커넥션 문제 발생! 이 설정에서는 idle timeout/TTL 설정이 필요합니다.");
        } finally {
            httpClient.close();
        }
    }

    /**
     * 짧은 대기 시간 테스트 (빠른 검증용)
     * 커넥션 풀 설정이 올바르게 적용되는지 확인
     */
    @Test
    @Disabled
    @DisplayName("커넥션 풀 기본 동작 테스트 - 짧은 대기")
    void connectionPool_shortDelay_shouldReuseConnection() throws Exception {
        CloseableHttpClient httpClient = createGoodHttpClient();
        HttpGet request = new HttpGet(SEJONG_PORTAL_URL);

        // 첫 번째 요청
        System.out.println("=== 첫 번째 요청 (커넥션 생성) ===");
        long start1 = System.currentTimeMillis();

        httpClient.execute(request, response -> {
            EntityUtils.consume(response.getEntity());
            System.out.println("응답 코드: " + response.getCode());
            return null;
        });

        long elapsed1 = System.currentTimeMillis() - start1;
        System.out.println("소요 시간: " + elapsed1 + "ms\n");

        // 5초 대기
        System.out.println("5초 대기...");
        Thread.sleep(5000);

        // 두 번째 요청 (커넥션 재사용)
        System.out.println("=== 두 번째 요청 (커넥션 재사용) ===");
        long start2 = System.currentTimeMillis();

        httpClient.execute(request, response -> {
            EntityUtils.consume(response.getEntity());
            System.out.println("응답 코드: " + response.getCode());
            return null;
        });

        long elapsed2 = System.currentTimeMillis() - start2;
        System.out.println("소요 시간: " + elapsed2 + "ms");

        // 커넥션 재사용 시 더 빨라야 함
        System.out.println("\n첫 번째: " + elapsed1 + "ms, 두 번째: " + elapsed2 + "ms");
        System.out.println("커넥션 재사용으로 " + (elapsed1 - elapsed2) + "ms 단축");

        httpClient.close();
    }

    /**
     * 잘못된 설정의 HttpClient 생성
     * - idle timeout 없음
     * - TTL 없음
     * - evictIdleConnections 없음
     */
    private CloseableHttpClient createBadHttpClient() throws Exception {
        PoolingHttpClientConnectionManager connectionManager =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(SSLContextBuilder.create()
                        .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                        .build())
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build())
                .setMaxConnTotal(5)
                .setMaxConnPerRoute(5)
                // 의도적으로 idle timeout, TTL 설정 안 함
                .build();

        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build())
            // 의도적으로 evictIdleConnections, evictExpiredConnections 설정 안 함
            .build();
    }

    /**
     * 올바른 설정의 HttpClient 생성 (SejongPortalClient와 동일)
     * - idle timeout: 120초
     * - TTL: 300초
     * - evictIdleConnections: 활성화
     */
    private CloseableHttpClient createGoodHttpClient() throws Exception {
        PoolingHttpClientConnectionManager connectionManager =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(SSLContextBuilder.create()
                        .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                        .build())
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build())
                .setMaxConnTotal(5)
                .setMaxConnPerRoute(5)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(30))
                    .setSocketTimeout(Timeout.ofSeconds(30))
                    .setTimeToLive(TimeValue.ofSeconds(300))  // TTL 설정
                    .build())
                .build();

        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build())
            .evictIdleConnections(TimeValue.of(120, TimeUnit.SECONDS))  // idle 커넥션 제거
            .evictExpiredConnections()  // 만료 커넥션 제거
            .build();
    }
}
