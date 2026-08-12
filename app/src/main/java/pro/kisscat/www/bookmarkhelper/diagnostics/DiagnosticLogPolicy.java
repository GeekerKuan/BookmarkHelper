package pro.kisscat.www.bookmarkhelper.diagnostics;

import java.util.regex.Pattern;

/** Exact allow-list for privacy-filtered session logs created by the modern logger. */
public final class DiagnosticLogPolicy {
    private static final Pattern SESSION_LOG = Pattern.compile(
            "session-[0-9]{8}-[0-9]{6}-[0-9]{3}-p[0-9]+\\.log");

    private DiagnosticLogPolicy() {
    }

    public static boolean isModernSessionLogName(String name) {
        return name != null && SESSION_LOG.matcher(name).matches();
    }
}
