package pro.kisscat.www.bookmarkhelper.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiagnosticRedactorTest {
    @Test
    public void rejectsSerializedBookmarkFieldsAsWholeLine() {
        String source = "2026-08-09 v tag {\"title\":\"Private\",\"url\":\"https://x\"}";

        assertEquals("[REDACTED_SENSITIVE_LINE]", DiagnosticRedactor.redactLine(source));
    }

    @Test
    public void removesUrlsEmailIpUuidAndPrivatePath() {
        String source = "open https://example.test/a?q=secret for me@example.test at 192.168.1.4 "
                + "id 7f55f5a8-69e3-4e5c-87bc-ddd166289ace "
                + "from /data/user/0/com.example/files/value";

        String safe = DiagnosticRedactor.redactLine(source);

        assertFalse(safe.contains("example.test"));
        assertFalse(safe.contains("192.168.1.4"));
        assertFalse(safe.contains("7f55f5a8"));
        assertFalse(safe.contains("/data/user"));
        assertTrue(safe.contains("<redacted-url>"));
        assertTrue(safe.contains("<redacted-email>"));
        assertTrue(safe.contains("<redacted-ip>"));
        assertTrue(safe.contains("<redacted-id>"));
        assertTrue(safe.contains("<redacted-path>"));
    }

    @Test
    public void rejectsCredentialAndChineseBookmarkLines() {
        assertEquals("[REDACTED_SENSITIVE_LINE]",
                DiagnosticRedactor.redactLine("access_token=secret-value"));
        assertEquals("[REDACTED_SENSITIVE_LINE]",
                DiagnosticRedactor.redactLine("书签数据：不应导出"));
    }

    @Test
    public void removesNetworkIdentifiersAndSelinuxCategories() {
        String source = "net 2001:db8:85a3::8a2e:370:7334 "
                + "mac 02:42:ac:11:00:02 "
                + "label u:object_r:app_data_file:s0:c123,c456";

        String safe = DiagnosticRedactor.redactLine(source);

        assertFalse(safe.contains("2001:db8"));
        assertFalse(safe.contains("02:42:ac"));
        assertFalse(safe.contains("c123,c456"));
        assertTrue(safe.contains("<redacted-ip>"));
        assertTrue(safe.contains("<redacted-mac>"));
        assertTrue(safe.contains("<redacted-mcs>"));
    }

    @Test
    public void removesBearerAndJwtTokensWithoutAKeyName() {
        String source = "header Bearer abcdefghijklmnop and "
                + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwcml2YXRlIn0.signatureValue";

        String safe = DiagnosticRedactor.redactLine(source);

        assertFalse(safe.contains("abcdefghijklmnop"));
        assertFalse(safe.contains("eyJhbGci"));
        assertTrue(safe.contains("<redacted-token>"));
    }

    @Test
    public void preservesUsefulStackAndCountMessages() {
        String source = "at pro.kisscat.Sync.run(Sync.java:42) bookmarks count=7";

        assertEquals(source, DiagnosticRedactor.redactLine(source));
    }

    @Test
    public void boundsEveryExportedLine() {
        String source = "x".repeat(DiagnosticRedactor.MAX_LINE_CHARACTERS + 200);

        String safe = DiagnosticRedactor.redactLine(source);

        assertTrue(safe.endsWith("...[TRUNCATED]"));
        assertTrue(safe.length() < source.length());
    }

    @Test
    public void onlyModernSessionLogNamesPassTheExportAllowList() {
        assertTrue(DiagnosticLogPolicy.isModernSessionLogName(
                "session-20260809-201530-123-p4567.log"));
        assertFalse(DiagnosticLogPolicy.isModernSessionLogName("2026-08-09Log.txt"));
        assertFalse(DiagnosticLogPolicy.isModernSessionLogName(
                "session-20260809-201530-123-p4567.log.bak"));
        assertFalse(DiagnosticLogPolicy.isModernSessionLogName(
                "../session-20260809-201530-123-p4567.log"));
    }
}
