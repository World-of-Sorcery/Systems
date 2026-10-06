package me.hektortm.woSSystems.systems.cscreens;

import me.hektortm.woSSystems.utils.Placeholders;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The decisions about a custom screen that need no server: whether an
 * element's conditions let it show, which buttons a screen type uses, and what
 * happens to a value a player typed before it goes into a command.
 */
public final class CscreenRules {

    /** A typed value is cut after this many characters. */
    static final int MAX_VALUE_LENGTH = 256;

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_]+");

    private CscreenRules() {}

    /**
     * Whether an element shows: without conditions always; with match type
     * {@code one} when any condition is met, otherwise when all are.
     */
    public static boolean shows(@Nullable String matchtype, int conditions, int met) {
        if (conditions == 0) return true;
        return "one".equalsIgnoreCase(matchtype) ? met > 0 : met == conditions;
    }

    /** An input's key as Minecraft accepts it: letters, digits and {@code _}. */
    public static boolean validKey(@Nullable String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /** The buttons a screen shows: its own in order, and the exit button below them (null: none). */
    public record Layout<T>(List<T> buttons, @Nullable T exit) {}

    /**
     * Which of the buttons that pass their conditions a screen of {@code type}
     * uses: a notice the first normal one, a confirmation the first two, a
     * button screen all of them plus the first exit button; a list of screens
     * and the server links only the exit button.
     *
     * @param passing the buttons whose conditions pass, in order
     * @param exit    which of them are exit buttons (same order)
     */
    public static <T> Layout<T> layout(String type, List<T> passing, List<Boolean> exit) {
        List<T> normal = new ArrayList<>();
        T exitButton = null;
        for (int i = 0; i < passing.size(); i++) {
            if (!exit.get(i)) normal.add(passing.get(i));
            else if (exitButton == null) exitButton = passing.get(i);
        }
        return switch (type) {
            case "confirmation" -> new Layout<>(first(normal, 2), null);
            case "buttons" -> new Layout<>(normal, exitButton);
            case "list", "links" -> new Layout<>(List.of(), exitButton);
            default -> new Layout<>(first(normal, 1), null); // notice
        };
    }

    private static <T> List<T> first(List<T> list, int count) {
        return list.size() <= count ? list : list.subList(0, count);
    }

    /**
     * A value a player typed or picked, made safe to stand in a command or a
     * message: line breaks and other control characters become spaces, the
     * colour sign is removed, {@code ](} is pulled apart so the value can't
     * form a clickable chat link ({@code [text](action:…)} runs a command),
     * and it is cut after {@link #MAX_VALUE_LENGTH} characters.
     */
    public static String clean(@Nullable String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_VALUE_LENGTH));
        for (int i = 0; i < value.length() && out.length() < MAX_VALUE_LENGTH; i++) {
            char c = value.charAt(i);
            if (c == '§') continue;
            if (Character.isISOControl(c)) c = ' ';
            if (c == '(' && out.length() > 0 && out.charAt(out.length() - 1) == ']') out.append(' ');
            out.append(c);
        }
        return out.toString().trim();
    }

    /** A slider's value as text: {@code 5} for 5.0, {@code 2.5} for 2.5. */
    public static String number(float value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) return String.valueOf((long) value);
        String text = String.format(java.util.Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * {@code text} with every {@code {input.<key>}} replaced by that input's
     * value; a key the screen doesn't have becomes empty. The values are put in
     * as they are (they are not read for placeholders themselves). With
     * {@code inputs} null (not a screen's commands) the text stays as written.
     */
    public static String fillInputs(String text, @Nullable Map<String, String> inputs) {
        if (inputs == null) return text;
        return Placeholders.replace(text, token ->
                "input".equals(token.namespace()) && token.key() != null && token.id() == null
                        ? inputs.getOrDefault(token.key(), "") : null);
    }
}
