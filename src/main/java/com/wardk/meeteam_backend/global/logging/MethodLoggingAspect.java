package com.wardk.meeteam_backend.global.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP를 사용하여 애플리케이션 전반의 메서드 실행을 로깅하는 클래스입니다.
 * 컨트롤러, 서비스, 웹훅, 보안 필터 등 다양한 계층의 메서드에 대한 실행 정보를 자동으로 기록합니다.
 * 
 * <p>
 * 로깅되는 정보:
 * </p>
 * <ul>
 * <li>메서드 시작 시점과 완료 시점</li>
 * <li>메서드 실행 시간</li>
 * <li>메서드 파라미터 값</li>
 * <li>메서드 반환 값</li>
 * <li>발생한 예외 정보</li>
 * </ul>
 */
@Aspect
@Component
public class MethodLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodLoggingAspect.class);

    /** 중복 로깅 방지를 위한 최근 로깅 메시지 캐시 */
    private static final Set<String> recentLogCache = new HashSet<>();

    /** 캐시 크기 제한 */
    private static final int CACHE_SIZE_LIMIT = 1000;

    /**
     * 컨트롤러 메서드 실행을 로깅하는 어드바이스입니다.
     * 
     * @Controller 또는 
     * @RestController 어노테이션이 있는 클래스의 모든 메서드에 적용됩니다.
     *
     * @param joinPoint 대상 메서드의 실행 지점
     * @return 대상 메서드의 반환 값
     * @throws Throwable 메서드 실행 중 발생할 수 있는 모든 예외
     */
    @Around("@within(org.springframework.stereotype.Controller) || " +
            "@within(org.springframework.web.bind.annotation.RestController)")
    public Object logControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecutionWithType(joinPoint, "Controller");
    }

    /**
     * 서비스 메서드 실행을 로깅하는 어드바이스입니다.
     * 
     * @Service 어노테이션이 있는 클래스의 모든 메서드에 적용됩니다.
     *          project 패키지 내 서비스는 제외합니다.
     *
     * @param joinPoint 대상 메서드의 실행 지점
     * @return 대상 메서드의 반환 값
     * @throws Throwable 메서드 실행 중 발생할 수 있는 모든 예외
     */
    @Around("@within(org.springframework.stereotype.Service) && " +
            "!within(com.wardk.meeteam_backend.domain.project..*)")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecutionWithType(joinPoint, "Service");
    }

    /**
     * 레포지토리 메서드 로깅을 위한 어드바이스입니다.
     * 필요한 경우 주석을 해제하여 사용할 수 있습니다.
     * 주의: 레포지토리 메서드는 많이 호출되어 로깅량이 많아질 수 있습니다.
     */
    // @Around("execution(* com.wardk.meeteam_backend.domain..*Repository.*(..))")
    // public Object logRepositoryMethod(ProceedingJoinPoint joinPoint) throws
    // Throwable {
    // return logMethodExecutionWithType(joinPoint, "Repository");
    // }

    /**
     * 웹훅 관련 메서드를 상세하게 로깅하는 어드바이스입니다.
     * domain.webhook 패키지 내 모든 클래스의 메서드에 적용됩니다.
     *
     * @param joinPoint 대상 메서드의 실행 지점
     * @return 대상 메서드의 반환 값
     * @throws Throwable 메서드 실행 중 발생할 수 있는 모든 예외
     */
    /*@Around("execution(* com.wardk.meeteam_backend.domain.webhook..*.*(..))")
    public Object logWebhookMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecutionWithType(joinPoint, "Webhook");
    } */

    /**
     * 보안 필터 관련 메서드를 상세하게 로깅하는 어드바이스입니다.
     * global.filter 패키지 내 모든 클래스의 메서드에 적용됩니다.
     * JWT 인증 및 기타 보안 관련 로직의 상세한 실행 정보를 기록합니다.
     *
     * @param joinPoint 대상 메서드의 실행 지점
     * @return 대상 메서드의 반환 값
     * @throws Throwable 메서드 실행 중 발생할 수 있는 모든 예외
     */
    @Around("execution(* com.wardk.meeteam_backend.global.filter..*.*(..))")
    public Object logSecurityMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecutionWithType(joinPoint, "Security");
    }

    /**
     * 메서드 실행을 로깅하는 핵심 메서드입니다.
     * 중복 로깅을 방지하고, INFO 레벨로 메서드 실행 정보를 기록합니다.
     *
     * @param joinPoint 대상 메서드의 실행 지점
     * @param type      메서드 유형 (예: "Controller", "Service" 등)
     * @return 대상 메서드의 반환 값
     * @throws Throwable 메서드 실행 중 발생할 수 있는 모든 예외
     */
    private Object logMethodExecutionWithType(ProceedingJoinPoint joinPoint, String type) throws Throwable {
        Logger targetLogger = LoggerFactory.getLogger(joinPoint.getTarget().getClass());
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // HTTP 요청 정보 (컨트롤러인 경우)
        String requestInfo = "";
        if ("Controller".equals(type)) {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                        .getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    requestInfo = String.format(" [%s %s]", request.getMethod(), request.getRequestURI());
                }
            } catch (Exception e) {
                // 예외 무시
            }
        }

        // 파라미터 요약
        String arguments = Arrays.stream(joinPoint.getArgs())
                .map(arg -> arg == null ? "null" : summarizeObject(arg))
                .collect(Collectors.joining(", "));

        // 로깅 키 생성 (중복 로깅 방지용)
        String logKey = className + "." + methodName + "(" + arguments + ")";

        // 캐시 크기 제한
        if (recentLogCache.size() > CACHE_SIZE_LIMIT) {
            recentLogCache.clear();
        }

        // 최근에 동일한 메서드가 로깅되었는지 확인
        boolean isDuplicate = !recentLogCache.add(logKey);

        // 중복 로그가 아니거나 중요한 컴포넌트의 로그인 경우에만 시작 로그 출력
        if (!isDuplicate || isImportantComponent(type)) {
            targetLogger.info("{} 시작: {}.{}{} - 파라미터: [{}]",
                    type, className, methodName, requestInfo, arguments);
        }

        // 메서드 실행 시간 측정
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            // 실제 메서드 실행
            Object result = joinPoint.proceed();
            stopWatch.stop();

            // 결과 요약
            String resultSummary = result == null ? "null" : summarizeObject(result);

            // 중복 로그가 아니거나 중요한 컴포넌트의 로그인 경우에만 완료 로그 출력
            if (!isDuplicate || isImportantComponent(type)) {
                targetLogger.info("{} 완료: {}.{}{} - 실행시간: {}ms - 결과: {}",
                        type, className, methodName, requestInfo,
                        stopWatch.getTotalTimeMillis(), resultSummary);
            }

            return result;
        } catch (Exception e) {
            stopWatch.stop();

            // 예외는 항상 로깅
            targetLogger.error("{} 예외: {}.{}{} - 실행시간: {}ms - 예외: {}",
                    type, className, methodName, requestInfo,
                    stopWatch.getTotalTimeMillis(), e.getMessage(), e);

            throw e;
        }
    }

    /**
     * 중요한 컴포넌트인지 확인합니다.
     * 이런 컴포넌트는 중복 로그가 있더라도 항상 로깅됩니다.
     * 
     * @param type 컴포넌트 유형
     * @return 중요 컴포넌트 여부
     */
    private boolean isImportantComponent(String type) {
        return "Controller".equals(type) || "Webhook".equals(type) || "Security".equals(type);
    }

    /**
     * 객체를 로깅하기 위해 요약하는 유틸리티 메서드입니다.
     * 너무 긴 문자열은 축약하고, 특별한 객체 타입은 간략하게 표시합니다.
     *
     * @param obj 요약할 객체
     * @return 객체의 문자열 요약본
     */
    private String summarizeObject(Object obj) {
        if (obj == null) {
            return "null";
        }

        try {
            // HTTP 요청 객체는 특별하게 처리
            if (obj instanceof HttpServletRequest) {
                HttpServletRequest req = (HttpServletRequest) obj;
                return String.format("HttpServletRequest[%s %s]", req.getMethod(), req.getRequestURI());
            }

            String str = obj.toString();

            // 너무 긴 문자열은 축약
            if (str.length() > 300) {
                return str.substring(0, 300) + "... (생략됨)";
            }

            return str;
        } catch (Exception e) {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(obj));
        }
    }

    /**
     * GitHub 웹훅 처리를 위한 특별 로깅 어드바이스입니다.
     * 웹훅 요청의 중요 헤더 정보를 추출하여 별도로 로깅합니다.
     *
     * @param joinPoint 대상 메서드의 실행 지점
     * @return 대상 메서드의 반환 값
     * @throws Throwable 메서드 실행 중 발생할 수 있는 모든 예외
     */
    @Around("execution(* com.wardk.meeteam_backend.web.webhook.controller.GithubWebhookController.handleWebhook(..))")
    public Object logWebhookPayload(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger targetLogger = LoggerFactory.getLogger(joinPoint.getTarget().getClass());

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                targetLogger.info("GitHub Webhook 수신: Event={}, Delivery={}, Signature={}",
                        request.getHeader("X-GitHub-Event"),
                        request.getHeader("X-GitHub-Delivery"),
                        maskSignature(request.getHeader("X-Hub-Signature-256")));
            }
        } catch (Exception e) {
            targetLogger.warn("GitHub Webhook 헤더 로깅 실패", e);
        }

        return joinPoint.proceed();
    }

    /**
     * 서명 값을 마스킹 처리합니다.
     * 보안을 위해 서명의 일부만 로깅합니다.
     * 
     * @param signature 마스킹할 서명 문자열
     * @return 마스킹된 서명 문자열
     */
    private String maskSignature(String signature) {
        if (signature == null || signature.length() < 10) {
            return "[마스킹됨]";
        }
        return signature.substring(0, 10) + "...";
    }
}