package com.wardk.meeteam_backend.domain.admin;

import com.wardk.meeteam_backend.global.exception.CustomException;
import com.wardk.meeteam_backend.global.response.ErrorCode;
import java.math.BigInteger;
import java.util.Locale;

public record AdminQueryOptions(int page, int size, String keyword, String sort) {
    public static AdminQueryOptions of(String page, String size, String keyword, String sort) {
        int number = 0;
        try {
            if (page != null && !page.isBlank()) number = Integer.parseInt(page);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (number < 0) throw invalid();
        int limit = 10;
        // Mock contract: missing/zero/non-numeric -> default, negatives -> 1, maximum 50.
        try {
            if (size != null && !size.isBlank()) {
                BigInteger parsed = new BigInteger(size);
                if (parsed.signum() != 0) limit = parsed.max(BigInteger.ONE)
                        .min(BigInteger.valueOf(50)).intValue();
            }
        } catch (NumberFormatException ignored) { }
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > 50 || (long) number * limit > Integer.MAX_VALUE) throw invalid();
        return new AdminQueryOptions(number, limit, search.toLowerCase(Locale.ROOT), sort);
    }

    public int offset() { return page * size; }
    /** '!' is the explicit SQL LIKE escape character, independent of MySQL backslash mode. */
    public String keywordPattern() {
        return "%" + keyword.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
    }
    public static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        try { return value == null ? null : Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private static CustomException invalid() {
        return new CustomException(ErrorCode.ADMIN_INVALID_REQUEST);
    }
}
