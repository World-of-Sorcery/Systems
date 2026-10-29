package me.hektortm.woSSystems.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sums inside texts: braces that hold a calculation, like
 * {@code {{stats.amount:a} + {stats.amount:b}}}, become its result once the
 * placeholders inside are filled in ({@code {5 + 3}} → {@code 8}). Pure; no
 * server needed.
 *
 * <p>A calculation holds numbers, {@code + - * / %}, {@code ^} (power),
 * brackets, {@code pi} and the functions {@code min}, {@code max} (two or more
 * values), {@code round}, {@code floor}, {@code ceil}, {@code abs},
 * {@code sqrt}, {@code sin}, {@code cos}, {@code tan} (radians) and
 * {@code rand()} (0 to 1). It needs at least one operator or function, so
 * {@code {5}} stays as written. The result shows up to two decimals and none
 * when it is whole ({@code 10 / 4} → {@code 2.5}, {@code 10 / 2} →
 * {@code 5}). Braces that hold anything else (a placeholder nobody knew, the
 * JSON of a {@code tellraw}, a typo, a division by zero) are left as written.</p>
 *
 * <p>{@link #compile} reads a calculation once with named variables and
 * evaluates it many times (the particle formulas of spells; wos-api checks the
 * same grammar in {@code internal/spellgraph/formula.go}).</p>
 */
public final class Maths {

    private Maths() {}

    /** A calculation read once; {@link #eval} takes the variables' values in the order they were named. */
    @FunctionalInterface
    public interface Formula {
        /** @throws IllegalArgumentException on a division or modulo by zero */
        double eval(double... vars);
    }

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
            double value = compile(expression).eval();
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return format(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reads a calculation that may use the named variables (case-insensitive).
     *
     * @throws IllegalArgumentException when it isn't one (a typo, an unknown name)
     */
    public static Formula compile(String expression, String... variables) {
        List<String> names = new ArrayList<>();
        for (String v : variables) names.add(v.toLowerCase(Locale.ROOT));
        Parser parser = new Parser(expression.toLowerCase(Locale.ROOT), names);
        Formula f = parser.expression();
        parser.skipSpaces();
        if (!parser.done()) throw new IllegalArgumentException("unexpected text at " + (parser.pos + 1));
        return f;
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
            if ("+-*/%^(".indexOf(c) >= 0) operator = true;
            else if (!(Character.isDigit(c) || c == '.' || c == ',' || c == ')' || c == ' ' || Character.isLetter(c))) return false;
        }
        return operator;
    }

    /**
     * expression = term (('+' | '-') term)*; term = unary (('*' | '/' | '%') unary)*;
     * unary = ('-' | '+') unary | power; power = primary ('^' unary)?.
     */
    private static final class Parser {
        private final String s;
        private final List<String> vars;
        private int pos;

        Parser(String s, List<String> vars) {
            this.s = s;
            this.vars = vars;
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

        Formula expression() {
            Formula value = term();
            while (true) {
                Formula a = value;
                if (eat('+')) {
                    Formula b = term();
                    value = v -> a.eval(v) + b.eval(v);
                } else if (eat('-')) {
                    Formula b = term();
                    value = v -> a.eval(v) - b.eval(v);
                } else return value;
            }
        }

        private Formula term() {
            Formula value = unary();
            while (true) {
                Formula a = value;
                if (eat('*')) {
                    Formula b = unary();
                    value = v -> a.eval(v) * b.eval(v);
                } else if (eat('/')) {
                    Formula b = unary();
                    value = v -> divide(a.eval(v), b.eval(v));
                } else if (eat('%')) {
                    Formula b = unary();
                    value = v -> modulo(a.eval(v), b.eval(v));
                } else return value;
            }
        }

        private static double divide(double a, double b) {
            if (b == 0) throw new IllegalArgumentException("division by zero");
            return a / b;
        }

        private static double modulo(double a, double b) {
            if (b == 0) throw new IllegalArgumentException("modulo by zero");
            return a % b;
        }

        private Formula unary() {
            if (eat('-')) {
                Formula a = unary();
                return v -> -a.eval(v);
            }
            if (eat('+')) return unary();
            Formula base = primary();
            if (!eat('^')) return base;
            Formula exp = unary();
            return v -> Math.pow(base.eval(v), exp.eval(v));
        }

        private Formula primary() {
            skipSpaces();
            if (eat('(')) {
                Formula value = expression();
                if (!eat(')')) throw new IllegalArgumentException("missing )");
                return value;
            }
            if (pos < s.length() && Character.isLetter(s.charAt(pos))) return name();
            return number();
        }

        private Formula number() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("number expected");
            double n = Double.parseDouble(s.substring(start, pos)); // NumberFormatException is an IllegalArgumentException
            return v -> n;
        }

        private Formula name() {
            int start = pos;
            while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
            String name = s.substring(start, pos);
            if (!eat('(')) {
                int index = vars.indexOf(name);
                if (index >= 0) return v -> v[index];
                if (name.equals("pi")) return v -> Math.PI;
                throw new IllegalArgumentException("( expected after " + name);
            }
            List<Formula> args = new ArrayList<>();
            if (!eat(')')) {
                args.add(expression());
                while (eat(',')) args.add(expression());
                if (!eat(')')) throw new IllegalArgumentException("missing )");
            }
            return apply(name, args);
        }

        private static Formula apply(String name, List<Formula> args) {
            switch (name) {
                case "min":
                case "max": {
                    if (args.size() < 2) throw new IllegalArgumentException(name + " needs two values");
                    boolean min = name.equals("min");
                    Formula[] all = args.toArray(new Formula[0]);
                    return v -> {
                        double out = all[0].eval(v);
                        for (Formula f : all) out = min ? Math.min(out, f.eval(v)) : Math.max(out, f.eval(v));
                        return out;
                    };
                }
                case "rand": {
                    if (!args.isEmpty()) throw new IllegalArgumentException("rand takes no value");
                    return v -> ThreadLocalRandom.current().nextDouble();
                }
                case "round": return one(name, args, a -> (double) Math.round(a));
                case "floor": return one(name, args, Math::floor);
                case "ceil": return one(name, args, Math::ceil);
                case "abs": return one(name, args, Math::abs);
                case "sqrt": return one(name, args, Math::sqrt);
                case "sin": return one(name, args, Math::sin);
                case "cos": return one(name, args, Math::cos);
                case "tan": return one(name, args, Math::tan);
                default: throw new IllegalArgumentException("unknown function " + name);
            }
        }

        private static Formula one(String name, List<Formula> args, java.util.function.DoubleUnaryOperator op) {
            if (args.size() != 1) throw new IllegalArgumentException(name + " takes one value");
            Formula a = args.get(0);
            return v -> op.applyAsDouble(a.eval(v));
        }
    }
}
