package com.wardk.meeteam_backend.global.auth.client;

import java.io.Console;
import java.util.Arrays;

/** Optional manual check: one real school login, no database, signup or profile output. */
public final class SchoolProfileCheckTool {
    private SchoolProfileCheckTool() { }

    public static void main(String[] args) {
        Console console = System.console();
        if (args.length != 0 || console == null) {
            System.err.println("터미널에서 인수 없이 실행해 주세요. 학번·비밀번호를 명령 인수로 전달하지 마세요.");
            System.exit(2);
            return;
        }
        char[] studentId = null;
        char[] password = null;
        int status = 1;
        try {
            console.printf("학교 인증과 이름·학과 조회만 확인합니다. 회원가입이나 DB 변경은 하지 않습니다.%n");
            console.printf("학번과 비밀번호 모두 숨김 입력됩니다. 채팅이나 코드에 입력하지 마세요.%n");
            studentId = console.readPassword("학번: ");
            password = console.readPassword("학교 비밀번호: ");
            if (studentId == null || password == null || studentId.length != 8
                    || password.length == 0 || password.length > 1024) {
                throw new IllegalArgumentException("Invalid input");
            }
            try (var client = new SejongPortalClient(stage -> diagnostic(console, stage))) {
                client.authenticateAndFetchProfile(new String(studentId), new String(password));
            }
            console.printf("학교 프로필 자동 조회와 학번 일치 검증에 성공했습니다. 개인정보 값은 표시하지 않습니다.%n");
            diagnostic(console, "CHECK_PASSED");
            status = 0;
        } catch (Exception e) {
            diagnostic(console, e instanceof com.wardk.meeteam_backend.global.exception.CustomException failure
                    ? "CHECK_FAILED_" + failure.getErrorCode().name() : "CHECK_FAILED_INPUT_OR_LOCAL_RUNTIME");
            console.printf("확인하지 못했습니다. 학교 포털 로그인 가능 여부와 네트워크를 확인해 주세요.%n");
        } finally {
            if (studentId != null) Arrays.fill(studentId, '\0');
            if (password != null) Arrays.fill(password, '\0');
        }
        if (status != 0) System.exit(status);
    }

    private static void diagnostic(Console console, String code) {
        console.printf("진단: %s%n", code);
        String output = System.getProperty("meeteam.profile.check.diagnostics");
        if (output != null && !output.isBlank()) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(output), code + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
                console.printf("진단 파일을 저장하지 못했습니다.%n");
            }
        }
    }
}
