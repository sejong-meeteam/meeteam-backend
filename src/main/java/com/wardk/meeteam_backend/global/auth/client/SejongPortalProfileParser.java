package com.wardk.meeteam_backend.global.auth.client;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

/** Parses only the single server-rendered portal profile card, never scripts. */
final class SejongPortalProfileParser {
    private SejongPortalProfileParser() { }

    static SejongPortalProfile parse(String html, String expectedStudentId) throws IOException {
        if (html == null || html.length() > 1_048_576) {
            throw new IllegalArgumentException("Invalid portal profile document");
        }
        ProfileCallback callback = new ProfileCallback();
        new ParserDelegator().parse(new StringReader(html), callback, true);
        if (callback.invalid || callback.cards != 1 || callback.cardDepth != -1
                || Arrays.stream(callback.fields).anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Invalid portal profile document");
        }
        String[] fields = Arrays.stream(callback.fields).map(SejongPortalProfileParser::normalize).toArray(String[]::new);
        if (Arrays.stream(fields).anyMatch(String::isBlank) || fields[2].length() > 100) {
            throw new IllegalArgumentException("Invalid portal profile document");
        }
        SejongPortalProfile profile = new SejongPortalProfile(fields[1], fields[0], fields[3]);
        if (!profile.studentId().equals(expectedStudentId)) {
            throw new IllegalArgumentException("Portal identity mismatch");
        }
        return profile;
    }

    private static String normalize(String text) {
        StringBuilder result = new StringBuilder();
        boolean space = false;
        for (int codePoint : text.codePoints().toArray()) {
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                space = result.length() > 0;
            } else {
                if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
                    throw new IllegalArgumentException("Invalid portal profile field");
                }
                if (space) result.append(' ');
                result.appendCodePoint(codePoint);
                space = false;
            }
        }
        return result.toString();
    }

    private static final class ProfileCallback extends HTMLEditorKit.ParserCallback {
        private int depth;
        private int cards;
        private int cardDepth = -1;
        private int fieldDepth = -1;
        private int currentField = -1;
        private boolean invalid;
        private final String[] fields = new String[4];
        private StringBuilder text;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            depth++;
            String classes = (String) attributes.getAttribute(HTML.Attribute.CLASS);
            if (tag == HTML.Tag.DIV && hasClass(classes, "text01")) {
                cards++;
                if (cardDepth != -1) invalid = true;
                cardDepth = depth;
            }
            if (tag == HTML.Tag.SPAN && cardDepth != -1) {
                for (int i = 0; i < fields.length; i++) {
                    if (hasClass(classes, "txt" + (i + 1))) {
                        if (currentField != -1 || fields[i] != null) invalid = true;
                        currentField = i;
                        fieldDepth = depth;
                        text = new StringBuilder();
                    }
                }
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (depth == fieldDepth) {
                fields[currentField] = text.toString();
                currentField = -1;
                fieldDepth = -1;
            }
            if (depth == cardDepth) cardDepth = -1;
            depth--;
        }

        @Override
        public void handleText(char[] data, int position) {
            if (currentField != -1) text.append(data);
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (currentField != -1 && tag == HTML.Tag.BR) text.append(' ');
        }

        private static boolean hasClass(String classes, String expected) {
            return classes != null && Arrays.asList(classes.split("\\s+")).contains(expected);
        }
    }
}
