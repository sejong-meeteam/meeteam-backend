package com.wardk.meeteam_backend.global.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;

/** Offline operator CLI, not a Spring bean or HTTP endpoint. Never accepts a password argument. */
public final class AdminPasswordSetupTool {
    private AdminPasswordSetupTool() {}

    public static void main(String[] args) {
        Console console = System.console();
        if (args.length != 1 || console == null) {
            System.err.println("일반 터미널에서 설정 도구를 직접 실행해주세요. 비밀번호를 명령 인수로 전달하지 마세요.");
            System.exit(2);
            return;
        }
        char[] password = null;
        char[] confirmation = null;
        int status = 0;
        try {
            console.printf("관리자 비밀번호를 직접 설정합니다. 20자 이상, UTF-8 최대 72바이트입니다.%n");
            console.printf("입력해도 글자나 별표가 보이지 않는 것이 정상입니다.%n");
            password = console.readPassword("새 비밀번호: ");
            confirmation = console.readPassword("같은 비밀번호 다시 입력: ");
            storeHash(Path.of(args[0]), password, confirmation);
            console.printf("암호화된 비밀번호를 비공개 파일에 저장했습니다. 값은 표시하지 않습니다.%n");
        } catch (Exception ignored) {
            System.err.println("비밀번호 설정에 실패했습니다. 길이·확인 입력·파일 권한을 확인해주세요. 기존 파일은 덮어쓰지 않습니다.");
            status = 2;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
        if (status != 0) System.exit(status);
    }

    public static void storeHash(Path output, char[] password, char[] confirmation) throws IOException {
        if (password == null || confirmation == null || password.length < 20 || !Arrays.equals(password, confirmation)) {
            throw new IllegalArgumentException("Password validation failed");
        }
        boolean blank = true;
        for (char ch : password) if (!Character.isWhitespace(ch)) { blank = false; break; }
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        try {
            if (blank || bytes.remaining() > 72) throw new IllegalArgumentException("Password validation failed");
        } finally {
            if (bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0);
        }
        String hash = new BCryptPasswordEncoder(12).encode(CharBuffer.wrap(password));
        boolean created = false;
        try {
            try (var channel = Files.newByteChannel(output, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                created = true;
                ByteBuffer encoded = StandardCharsets.US_ASCII.encode(hash + "\n");
                while (encoded.hasRemaining()) channel.write(encoded);
            }
        } catch (IOException failure) {
            if (created) Files.deleteIfExists(output);
            throw failure;
        }
    }
}
