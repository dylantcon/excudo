package com.excudo.console.utils;

/**
 * ANSI terminal color utility with graceful degradation.
 * Colors are disabled when output is piped/redirected or terminal is dumb.
 */
public final class ConsoleColors {

    private static final boolean ENABLED;

    static {
        ENABLED = System.console() != null
                && !"dumb".equals(System.getenv("TERM"));
    }

    private static final String RESET = "\033[0m";

    private ConsoleColors() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static String success(String text) {
        return ENABLED ? "\033[32m" + text + RESET : text;
    }

    public static String error(String text) {
        return ENABLED ? "\033[31m" + text + RESET : text;
    }

    public static String header(String text) {
        return ENABLED ? "\033[1;36m" + text + RESET : text;
    }

    public static String accent(String text) {
        return ENABLED ? "\033[33m" + text + RESET : text;
    }

    public static String bold(String text) {
        return ENABLED ? "\033[1m" + text + RESET : text;
    }

    public static String dim(String text) {
        return ENABLED ? "\033[2m" + text + RESET : text;
    }
}
