package com.apps.mindlog.global.validation;

public final class VisibleText {
    private VisibleText() {}

    /** Strip surrounding space, preserving internal graphemes such as joined emoji. */
    public static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isBoundarySpace(value.charAt(start))) start++;
        while (end > start && isBoundarySpace(value.charAt(end - 1))) end--;
        String text = value.substring(start, end);
        boolean hasContent = text.codePoints().anyMatch(cp ->
                !isBoundarySpace(cp) && Character.getType(cp) != Character.FORMAT);
        return hasContent ? text : "";
    }

    private static boolean isBoundarySpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x200B;
    }
}
