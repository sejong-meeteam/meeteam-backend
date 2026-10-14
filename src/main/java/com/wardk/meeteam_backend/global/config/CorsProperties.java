package com.wardk.meeteam_backend.global.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @ConfigurationProperties 를 통해서 타입검증 완료 -> 타입이 맞지 않을경우 에러발생
 * @NotEmpty 를 통해서 주입이 안되는 상황에 예외 발생
 */
@Component
@ConfigurationProperties(prefix = "cors")
@Getter
@Setter
public class CorsProperties {

    @NotEmpty
    private List<String> allowedOrigins;
    @NotEmpty
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private boolean allowCredentials = true;
    private long maxAge = 3600L;
}
