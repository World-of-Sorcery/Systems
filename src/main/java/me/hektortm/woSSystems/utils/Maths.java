package me.hektortm.woSSystems.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Sums inside texts: braces that hold a calculation, like
 * {@code {{stats.amount:a} + {stats.amount:b}}}, become its result once the
 * placeholders inside are filled in ({@code {5 + 3}} → {@code 8}). Pure; no
 * server needed.
 *
 * <p>A calculation holds numbers, {@code + - * / %}, brackets and the
 * functions {@code min}, {@code max} (two or more values), {@code round},
 * {@code floor}, {@code ceil} and {@code abs}. It needs at least one operator
 * or function, so {@code {5}} stays as written. The result shows up to two
 * decimals and none when it is whole ({@code 10 / 4} → {@code 2.5},
 * {@code 10 / 2} → {@code 5}). Braces that hold anything else (a placeholder
 * nobody knew, the JSON of a {@code tellraw}, a typo, a division by zero) are
 * left as written.</p>
 */
public final class Maths {

    private Maths() {}

    /** {@code text} with every calculation in braces replaced by its result; inner braces first. */
    public static String fill(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        Deque<Integer> open = new ArrayDeque<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                open.push(out.length());
                out.append(c);
                continue;
            }
            if (c != '}' || open.isEmpty()) {
                out.append(c);
                continue;
            }
            int start = open.pop();
            String result = calculate(out.substring(start + 1));
            if (result == null) {
                out.append(c);
            } else {
                out.setLength(start);
                out.append(result);
            }
        }
        return out.toString();
    }

    /** The result of a calculation as text, or null if {@code expression} isn't one. */
    public static String calculate(String expression) {
        if (!looksLikeMaths(expression)) return null;
        try {
            Parser parser = new Parser(expression.toLowerCase(Locale.ROOT));
            double value = parser.expression();
            parser.skipSpaces();
            if (!parser.done() || Double.isNaN(value) || Double.isInfinite(value)) return null;
            return format(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A number as the calculations show it: up to two decimals, none when whole. */
    public static String format(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.signum() == 0) return "0";
        return rounded.toPlainString();
    }

    /** Only calculation characters, and at least one operator or function. */
    private static boolean looksLikeMaths(String s) {
        boolean operator = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("+-*/%(".indexOf(c) >= 0) operator = true;
            else if (!(Character.isDigit(c) || c == '.' || c == ',' || c == ')' || c == ' ' || Character.isLetter(c))) return false;
        }
        return operator;
    }

    /** expression = term (('+' | '-') term)*; term = unary (('*' | '/' | '%') unary)*. */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean done() {
            return pos >= s.length();
        }

        void skipSpaces() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        private boolean eat(char c) {
            skipSpaces();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        double expression() {
            double value = term();
            while (true) {
                if (eat('+')) value += term();
                else if (eat('-')) value -= term();
                else return value;
            }
        }

        private double term() {
            double value = unary();
            while (true) {
                if (eat('*')) value *= unary();
                else if (eat('/')) value = divide(value, unary());
                else if (eat('%')) {
                    double by = unary();
                    if (by == 0) throw new IllegalArgumentException("modulo by zero");
                    value %= by;
                } else return value;
            }
        }

        private static double divide(double a, double b) {
            if (b == 0) throw new IllegalArgumentException("division by zero");
            return a / b;
        }

        private double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            return primary();
        }

        private double primary() {
            skipSpaces();
            if (eat('(')) {
                double value = expression();
                if (!eat(')')) throw new IllegalArgumentException("missing )");
                return value;
            }
            if (pos < s.length() && Character.isLetter(s.charAt(pos))) return function();
            return number();
        }

        private double number() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("number expected");
            return Double.parseDouble(s.substring(start, pos)); // NumberFormatException is an IllegalArgumentException
        }

        private double function() {
            int start = pos;
            while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
            String name = s.substring(start, pos);
            if (!eat('(')) throw new IllegalArgumentException("( expected after " + name);
            List<Double> args = new ArrayList<>();
            args.add(expression());
            while (eat(',')) args.add(expression());
            if (!eat(')')) throw new IllegalArgumentException("missing )");
            return apply(name, args);
        }

        private static double apply(String name, List<Double> args) {
            switch (name) {
                case "min":
                case "max": {
                    if (args.size() < 2) throw new IllegalArgumentException(name + " needs two values");
                    double out = args.get(0);
                    for (double v : args) out = name.equals("min") ? Math.min(out, v) : Math.max(out, v);
                    return out;
                }
                case "round": return one(name, args, Math.round(args.get(0)));
                case "floor": return one(name, args, Math.floor(args.get(0)));
                case "ceil": return one(name, args, Math.ceil(args.get(0)));
                case "abs": return one(name, args, Math.abs(args.get(0)));
                default: throw new IllegalArgumentException("unknown function " + name);
            }
        }

        private static double one(String name, List<Double> args, double result) {
            if (args.size() != 1) throw new IllegalArgumentException(name + " takes one value");
            return result;
        }
    }
}
