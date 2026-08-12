package pro.kisscat.www.bookmarkhelper.diagnostics;

import java.util.regex.Pattern;

/**
 * Conservative, deterministic redaction for application logs.
 *
 * <p>This class deliberately has no Android dependencies so the exact export
 * policy can be covered by local unit tests. Log messages pass through it once
 * before being stored and again while a diagnostic archive is built.</p>
 */
public final class DiagnosticRedactor {
    public static final int MAX_LINE_CHARACTERS = 4_096;

    private static final String REDACTED_LINE = "[REDACTED_SENSITIVE_LINE]";
    private static final Pattern SENSITIVE_STRUCTURED_LINE = Pattern.compile(
            "(?i)(?:"
                    + "[\\\"']?(?:url|title|folder|foldername|bookmarkdata|bookmark_data)"
                    + "[\\\"']?\\s*[:=]"
                    + "|[\\\"'](?:url|title|folder|children)[\\\"']\\s*:"
                    + "|(?:书签|收藏)(?:数据|内容)\\s*[：:]"
                    + "|(?:android[ _-]?id|serial(?:number)?|account(?:name|id)|email|"
                    + "access[ _-]?token|refresh[ _-]?token|token|authorization|cookie|"
                    + "password|passwd|secret)\\s*[:=]"
                    + ")");
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:\\b(?:https?|ftp|file|content|chrome|edge|about)://[^\\s<>\\\"']+"
                    + "|\\b(?:data|javascript|mailto|tel):[^\\s<>\\\"']+"
                    + "|\\bwww\\.[^\\s<>\\\"']+)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])");
    private static final Pattern IPV6_FULL = Pattern.compile(
            "(?i)(?<![0-9a-f:])(?:[0-9a-f]{1,4}:){3,7}[0-9a-f]{1,4}"
                    + "(?![0-9a-f:])");
    private static final Pattern IPV6_COMPRESSED = Pattern.compile(
            "(?i)(?<![0-9a-f:])(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4})*)?::"
                    + "(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4})*)?(?![0-9a-f:])");
    private static final Pattern MAC_ADDRESS = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b");
    private static final Pattern SELINUX_MCS = Pattern.compile(
            "(?i)\\bs0(?::c[0-9]+(?:,c[0-9]+)*)\\b");
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-"
                    + "[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final Pattern LONG_HEX = Pattern.compile("(?i)\\b[0-9a-f]{24,}\\b");
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{8,}=*");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\b");
    private static final Pattern PRIVATE_PATH = Pattern.compile(
            "(?i)(?:/(?:data|storage|sdcard|mnt)/(?:[^\\s,;:]+/?)+)");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile(
            "[\\p{Cc}&&[^\\r\\n\\t]]");

    private DiagnosticRedactor() {
    }

    public static String redactLine(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String result = CONTROL_CHARACTERS.matcher(input).replaceAll("?");
        if (SENSITIVE_STRUCTURED_LINE.matcher(result).find()) {
            return REDACTED_LINE;
        }

        result = URL.matcher(result).replaceAll("<redacted-url>");
        result = EMAIL.matcher(result).replaceAll("<redacted-email>");
        result = IPV4.matcher(result).replaceAll("<redacted-ip>");
        result = MAC_ADDRESS.matcher(result).replaceAll("<redacted-mac>");
        result = IPV6_FULL.matcher(result).replaceAll("<redacted-ip>");
        result = IPV6_COMPRESSED.matcher(result).replaceAll("<redacted-ip>");
        result = SELINUX_MCS.matcher(result).replaceAll("<redacted-mcs>");
        result = UUID.matcher(result).replaceAll("<redacted-id>");
        result = BEARER_TOKEN.matcher(result).replaceAll("<redacted-token>");
        result = JWT.matcher(result).replaceAll("<redacted-token>");
        result = LONG_HEX.matcher(result).replaceAll("<redacted-token>");
        result = PRIVATE_PATH.matcher(result).replaceAll("<redacted-path>");

        if (result.length() > MAX_LINE_CHARACTERS) {
            result = result.substring(0, MAX_LINE_CHARACTERS)
                    + "...[TRUNCATED]";
        }
        return result;
    }
}
