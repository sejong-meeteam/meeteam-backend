package com.wardk.meeteam_backend.global.aop.aspect;


import jakarta.annotation.PostConstruct;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.hibernate.StaleObjectStateException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Order(Ordered.LOWEST_PRECEDENCE - 1)
@Aspect
@Component
@RequiredArgsConstructor
public class OptimisticLockRetryAspect {

    private final MeterRegistry meterRegistry;


    private static final int MAX_RETRIES = 5;
    private static final int RETRY_DELAY_MS = 30;

    @Pointcut("@annotation(com.wardk.meeteam_backend.global.aop.Retry)")
    public void retry() {
    }


    @Around("retry()")
    public Object retryOptimisticLock(ProceedingJoinPoint joinPoint) throws Throwable {
        Exception exceptionHolder = null;
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        int attempts = 0;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Object result = joinPoint.proceed();
                if (attempts > 0) {
                    // “재시도 끝에 복구된 호출” 카운트
                    meterRegistry.counter("optimistic.lock.retry",
                            "class", className,
                            "method", methodName,
                            "outcome", "recovered")
                        .increment();
                }
                return result;
            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
                exceptionHolder = e;
                attempts++;
                // 충돌 발생 빈도 파악
                meterRegistry.counter("optimistic.lock.retry",
                        "class", className,
                        "method", methodName,
                        "outcome", "attempt",
                        "exception", e.getClass().getSimpleName())
                    .increment();
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        // 모든 시도 소진 후 throw 직전에 1회 증가 → 알람/장애 감지용
        meterRegistry.counter("optimistic.lock.retry",
                "class", className,
                "method", methodName,
                "outcome", "exhausted")
            .increment();
        throw exceptionHolder;
    }

    @PostConstruct
    public void initMetrics() {
        String className = "default";
        String methodName = "none";

        meterRegistry.counter("optimistic.lock.retry",
                "class", className,
                "method", methodName,
                "outcome", "attempt").increment(0.0);

        meterRegistry.counter("optimistic.lock.retry",
                "class", className,
                "method", methodName,
                "outcome", "recovered").increment(0.0);

        meterRegistry.counter("optimistic.lock.retry",
                "class", className,
                "method", methodName,
                "outcome", "exhausted").increment(0.0);
    }

}
