package me.hektortm.woSSystems.utils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The "Compare values" conditions ({@code compare_<how>}): the condition's
 * value and its parameter, both with their placeholders filled in, compared
 * with each other. Pure; no server needed.
 *
 * <p>Two numbers are compared as numbers ({@code 10} equals {@code 10.0}).
 * Anything else is compared as text, without colour codes, spaces around it
 * and case ({@code &aYes} equals {@code yes}); a text is never greater or
 * less than another.</p>
 */
public final class Compare {

    private Compare() {}

    /** Whether {@code name} is one of the compare conditions. */
    public static boolean is(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("compare_");
    }

    /** The compare condition {@code name} for {@code left} and {@code right}; false for an unknown one. */
    public static boolean test(String name, String left, String right) {
        String how = name.toLowerCase(Locale.ROOT).substring("compare_".length());
        BigDecimal a = number(left);
        BigDecimal b = number(right);
        if (a != null && b != null) {
            int c = a.compareTo(b);
            return switch (how) {
                case "greater_than" -> c > 0;
                case "at_least" -> c >= 0;
                case "less_than" -> c < 0;
                case "at_most" -> c <= 0;
                case "equal_to" -> c == 0;
                case "not_equal_to" -> c != 0;
                default -> false;
            };
        }
        boolean same = text(left).equalsIgnoreCase(text(right));
        return switch (how) {
            case "equal_to" -> same;
            case "not_equal_to" -> !same;
            default -> false;
        };
    }

    /** The number {@code s} holds, or null. */
    static BigDecimal number(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.replaceAll("(?i)[§&]([0-9a-fk-or]|#[0-9a-f]{6})", "").replaceAll("(?i)#[0-9a-f]{6}", "").trim();
    }
}
