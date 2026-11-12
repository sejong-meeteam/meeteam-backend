package com.wardk.meeteam_backend.schoolregistrationunit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wardk.meeteam_backend.global.auth.client.SejongPortalProfile;
import com.wardk.meeteam_backend.global.auth.repository.SejongCodeRepository;
import com.wardk.meeteam_backend.global.auth.service.dto.SejongRegisterInfo;
import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Only a mocked Redis transport; verifies the actual JSON codec and atomic consumption API. */
@ExtendWith(MockitoExtension.class)
class SchoolRegistrationCodeRepositoryTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    private final ObjectMapper mapper = new ObjectMapper();
    private SejongCodeRepository codes;
    @BeforeEach void setup() {
        codes = new SejongCodeRepository(redis, mapper);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test void codeStoresFullVerifiedIdentityWithTenMinuteTtlAndConsumesAtomically() throws Exception {
        var info = new SejongRegisterInfo("20261234", "Synthetic School Name", "Synthetic Department");
        String code = codes.saveRegisterInfo(info);
        var key = ArgumentCaptor.forClass(String.class);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), payload.capture(), eq(Duration.ofMinutes(10)));
        assertThat(key.getValue()).isEqualTo("sejong:code:" + code);
        var json = mapper.readTree(payload.getValue());
        assertThat(json.path("studentId").asText()).isEqualTo("20261234");
        assertThat(json.path("name").asText()).isEqualTo("Synthetic School Name");
        assertThat(json.path("department").asText()).isEqualTo("Synthetic Department");
        when(values.getAndDelete(key.getValue())).thenReturn(payload.getValue()).thenReturn(null);
        assertThat(codes.consumeRegisterInfo(code)).get().satisfies(actual ->
                assertThat(actual.requireVerifiedProfile()).isEqualTo(info.requireVerifiedProfile()));
        assertThat(codes.consumeRegisterInfo(code)).isEmpty();
        verify(values, times(2)).getAndDelete(key.getValue());
        verify(values, never()).get(anyString());
    }

    @Test void malformedJsonIsConsumedAndRejected() {
        when(values.getAndDelete("sejong:code:synthetic-code")).thenReturn("{malformed-json");
        assertThat(codes.consumeRegisterInfo("synthetic-code")).isEmpty();
        verify(values).getAndDelete("sejong:code:synthetic-code");
    }

    @Test void legacyStoredJsonDeserializesButCannotBecomeVerifiedProfile() {
        when(values.getAndDelete("sejong:code:synthetic-code")).thenReturn("{\"studentId\":\"20261234\"}");
        var info = codes.consumeRegisterInfo("synthetic-code").orElseThrow();
        assertThatThrownBy(info::requireVerifiedProfile).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEJONG_CODE));
    }

    @Test void storageLogsAndDtoRepresentationsDoNotExposeSchoolIdentityOrCode() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SejongCodeRepository.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var info = new SejongRegisterInfo("20261234", "Synthetic School Name", "Synthetic Department");
            String code = codes.saveRegisterInfo(info);
            when(values.getAndDelete("sejong:code:" + code)).thenReturn(mapper.writeValueAsString(info));
            codes.consumeRegisterInfo(code);
            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("20261234", "Synthetic School Name", "Synthetic Department", code);
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(info.toString()).isEqualTo("SejongRegisterInfo[redacted]");
            assertThat(new SejongPortalProfile("20261234", "Synthetic School Name", "Synthetic Department").toString())
                    .isEqualTo("SejongPortalProfile[redacted]");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
