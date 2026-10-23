package me.hektortm.woSSystems.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Finds {@code {placeholder}} tokens in text and swaps in their values. Pure:
 * what a token means is up to the caller ({@link PlaceholderResolver}).
 *
 * <p>A token is {@code {name}}, {@code {namespace.key}} or
 * {@code {namespace.key:id}}: letters, digits and {@code _} (the id may also
 * hold {@code - . : /}). Anything else between braces, like the JSON of a
 * {@code tellraw} command, is left as it is, and so is a token whose lookup
 * returns {@code null} (unknown, so a typo stays visible).</p>
 */
public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("(glyph|shift):[A-Za-z0-9_-]+|[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+(:[A-Za-z0-9_.:/-]+)?)?");

    /** A token split into its parts; {@code key} and {@code id} are null when absent. */
    public record Token(String raw, String namespace, String key, String id) {
        /** A token without a namespace: {@code {name}}. */
        public boolean isBare() { return key == null; }
    }

    private Placeholders() {}

    /** The token, or null if {@code raw} (without braces) isn't one. */
    public static Token parse(String raw) {
        if (!TOKEN.matcher(raw).matches()) return null;
        if (raw.startsWith("glyph:") || raw.startsWith("shift:")) {
            int c = raw.indexOf(':');
            return new Token(raw, raw.substring(0, c), "", raw.substring(c + 1));
        }
        int dot = raw.indexOf('.');
        if (dot < 0) return new Token(raw, raw, null, null);
        String namespace = raw.substring(0, dot);
        String rest = raw.substring(dot + 1);
        int colon = rest.indexOf(':');
        return colon < 0
                ? new Token(raw, namespace, rest, null)
                : new Token(raw, namespace, rest.substring(0, colon), rest.substring(colon + 1));
    }

    /** {@code input} with every token that {@code lookup} knows replaced by its value. */
    public static String replace(String input, Function<Token, String> lookup) {
        if (input == null || input.indexOf('{') < 0) return input;
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        while (i < input.length()) {
            int open = input.indexOf('{', i);
            int close = open < 0 ? -1 : input.indexOf('}', open + 1);
            if (close < 0) {
                out.append(input, i, input.length());
                break;
            }
            // A '{' inside the braces: this one isn't a token, look again from the inner one.
            int inner = input.indexOf('{', open + 1);
            if (inner >= 0 && inner < close) {
                out.append(input, i, inner);
                i = inner;
                continue;
            }
            out.append(input, i, open);
            Token token = parse(input.substring(open + 1, close));
            String value = token == null ? null : lookup.apply(token);
            out.append(value != null ? value : input.substring(open, close + 1));
            i = close + 1;
        }
        return out.toString();
    }

    /**
     * Lines with their tokens replaced; a value with line breaks (like a custom
     * item's lore) becomes several lines.
     */
    public static List<String> replaceLines(List<String> lines, Function<Token, String> lookup) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String replaced = replace(line, lookup);
            if (replaced == null || replaced.indexOf('\n') < 0) out.add(replaced);
            else out.addAll(List.of(replaced.split("\n", -1)));
        }
        return out;
    }
}
