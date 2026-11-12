package com.wardk.meeteam_backend.global.auth.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SejongPortalProfileParserTest {
    private static final String ID = "24001234";

    static String card(String name, String id, String department) {
        return "<div class='other text01'><span class='txt1'>" + name
                + "</span><span class='txt2'>" + id
                + "</span><span class='txt3'>학부</span><span class='txt4'>"
                + department + "</span></div>";
    }

    @Test
    void extractsUnicodeEntitiesAndNormalizesSpacing() throws Exception {
        SejongPortalProfile result = SejongPortalProfileParser.parse(
                "<html><body>" + card("  홍&nbsp; 길동 ", "  " + ID + "  ", "컴퓨터 &amp;\n 인공지능학과") + "</body></html>", ID);
        assertEquals(new SejongPortalProfile(ID, "홍 길동", "컴퓨터 & 인공지능학과"), result);
        assertFalse(result.toString().contains(ID));
        assertFalse(result.toString().contains("홍"));
    }

    @Test
    void rejectsIdentityMismatch() {
        assertThrows(IllegalArgumentException.class, () -> SejongPortalProfileParser.parse(card("홍길동", "24009999", "컴퓨터공학과"), ID));
    }

    @Test
    void rejectsMissingBlankDuplicateOrAmbiguousFields() {
        String valid = card("홍길동", ID, "컴퓨터공학과");
        String[] invalidDocuments = {
                valid.replace("<span class='txt4'>컴퓨터공학과</span>", ""),
                valid.replace("컴퓨터공학과", "&nbsp; \n"),
                valid.replace("</div>", "<span class='txt1'>다른 이름</span></div>"),
                valid.replace("class='txt1'", "class='txt1 txt4'"),
                valid + valid,
                valid.replace("<span class='txt3'>학부</span>", ""),
                "<html><form action='/login'><input name='password'></form></html>"
        };
        for (String html : invalidDocuments) {
            assertThrows(IllegalArgumentException.class, () -> SejongPortalProfileParser.parse(html, ID));
        }
    }

    @Test
    void recordRejectsUnboundedOrUnsafeValues() {
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile("1234567", "홍길동", "학과"));
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile("１２３４５６７８", "홍길동", "학과"));
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile(ID, "홍\n길동", "학과"));
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile(ID, "홍\u200b길동", "학과"));
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile(ID, "x".repeat(101), "학과"));
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile(ID, "홍길동", "x".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> new SejongPortalProfile(ID, "\u00a0", "학과"));
    }
}
