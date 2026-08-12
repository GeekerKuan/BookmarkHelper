package pro.kisscat.www.bookmarkhelper.diagnostics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/** Builds a small, allow-listed diagnostic archive in app-private cache. */
public final class DiagnosticExporter {
    private static final String VIA_PACKAGE = "mark.via";
    private static final String EDGE_PACKAGE = "com.microsoft.emmx";
    private static final String EXPORT_DIRECTORY = "diagnostics_exports";
    private static final int MAX_RETAINED_EXPORTS = 3;
    private static final int MAX_BACKUP_ENTRIES = 20;
    private static final int MAX_UNKNOWN_ENTRIES = 5;
    private static final long MAX_UNKNOWN_PROPERTIES_BYTES = 64L * 1_024L;
    private static final int MAX_LOG_LINES = 2_000;
    private static final int MAX_RAW_LOG_BYTES = 384 * 1_024;
    private static final int MAX_REDACTED_LOG_BYTES = 512 * 1_024;
    private static final long MAX_ARCHIVE_BYTES = 1_500_000L;
    private static final ReentrantLock DIAGNOSTIC_LOCK = new ReentrantLock();

    private DiagnosticExporter() {
    }

    public static File create(Context sourceContext) throws IOException {
        DIAGNOSTIC_LOCK.lock();
        try {
            return createLocked(sourceContext);
        } finally {
            DIAGNOSTIC_LOCK.unlock();
        }
    }

    private static File createLocked(Context sourceContext) throws IOException {
        Context context = sourceContext.getApplicationContext();
        LogHelper.writeNow();

        File exportDirectory = new File(context.getCacheDir(), EXPORT_DIRECTORY);
        ensureDirectory(exportDirectory);
        pruneExports(exportDirectory, MAX_RETAINED_EXPORTS - 1);

        String timestamp = timestamp(System.currentTimeMillis(), "yyyyMMdd-HHmmss-SSS");
        File target = new File(exportDirectory,
                "BookmarkHelper-diagnostics-" + timestamp + ".zip");
        File temporary = new File(exportDirectory, target.getName() + ".tmp");
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Unable to replace an old diagnostic temporary file");
        }

        byte[] metadata = buildMetadata(context).getBytes(StandardCharsets.UTF_8);
        List<LogHelper.SessionLogSnapshot> sessionLogs =
                LogHelper.snapshotRecentSessions(MAX_RAW_LOG_BYTES, 2);
        int redactedLimitPerSession = MAX_REDACTED_LOG_BYTES / sessionLogs.size();
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temporary, false))) {
                writeEntry(zip, "diagnostics.txt", metadata);
                for (LogHelper.SessionLogSnapshot sessionLog : sessionLogs) {
                    String entryName = sessionLog.isCurrentSession()
                            ? "logs/current-session-redacted.txt"
                            : "logs/previous-session-redacted.txt";
                    writeEntry(zip, entryName, buildRedactedSessionLog(
                            sessionLog, redactedLimitPerSession));
                }
            }
            if (!temporary.isFile() || temporary.length() == 0L
                    || temporary.length() > MAX_ARCHIVE_BYTES) {
                throw new IOException("Diagnostic archive exceeded its safe size limit");
            }
            moveAtomically(temporary, target);
            return target;
        } finally {
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }

    private static String buildMetadata(Context context) {
        StringBuilder output = new StringBuilder();
        output.append("BookmarkHelper redacted diagnostics\n")
                .append("privacy=no bookmark/database/backup contents; no title/url/account/")
                .append("serial/android_id; no logcat\n")
                .append("logs.policy=current plus previous modern session only; legacy logs excluded\n")
                .append("created_utc=")
                .append(timestamp(System.currentTimeMillis(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
                .append('\n');

        appendPackage(output, context, "app", context.getPackageName());
        output.append("android.sdk=").append(Build.VERSION.SDK_INT).append('\n')
                .append("android.release=").append(safePublicValue(Build.VERSION.RELEASE)).append('\n')
                .append("android.security_patch=")
                .append(safePublicValue(Build.VERSION.SECURITY_PATCH)).append('\n')
                .append("android.manufacturer=")
                .append(safePublicValue(Build.MANUFACTURER)).append('\n')
                .append("android.model=").append(safePublicValue(Build.MODEL)).append('\n')
                .append("android.supported_abis=")
                .append(safePublicValue(String.join(",", Build.SUPPORTED_ABIS))).append('\n');
        appendPackage(output, context, "via", VIA_PACKAGE);
        appendPackage(output, context, "edge", EDGE_PACKAGE);
        appendUnknownSummary(output, context);
        appendBackupSummary(output, context);
        return output.toString();
    }

    private static void appendPackage(
            StringBuilder output, Context context, String label, String packageName) {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = context.getPackageManager().getPackageInfo(
                        packageName, PackageManager.PackageInfoFlags.of(0L));
            } else {
                // Android 12 compatibility.
                //noinspection deprecation
                info = context.getPackageManager().getPackageInfo(packageName, 0);
            }
            output.append(label).append(".installed=true\n")
                    .append(label).append(".package=").append(packageName).append('\n')
                    .append(label).append(".version_name=")
                    .append(safePublicValue(info.versionName)).append('\n')
                    .append(label).append(".version_code=")
                    .append(info.getLongVersionCode()).append('\n');
        } catch (PackageManager.NameNotFoundException missing) {
            output.append(label).append(".installed=false\n")
                    .append(label).append(".package=").append(packageName).append('\n');
        } catch (RuntimeException error) {
            output.append(label).append(".installed=unknown\n")
                    .append(label).append(".package=").append(packageName).append('\n')
                    .append(label).append(".version_error=")
                    .append(error.getClass().getSimpleName()).append('\n');
        }
    }

    private static void appendUnknownSummary(StringBuilder output, Context context) {
        File directory = new File(context.getFilesDir(), "root_transactions");
        File[] files = directory.listFiles(file -> file.isFile()
                && !Files.isSymbolicLink(file.toPath())
                && file.getName().endsWith(".unknown.properties"));
        if (files == null) {
            output.append("root_unknown.status=")
                    .append(directory.isDirectory() ? "unreadable" : "none")
                    .append('\n');
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        output.append("root_unknown.status=")
                .append(files.length == 0 ? "none" : "present").append('\n')
                .append("root_unknown.count=").append(files.length).append('\n');
        int count = Math.min(files.length, MAX_UNKNOWN_ENTRIES);
        for (int index = 0; index < count; index++) {
            File file = files[index];
            String prefix = "root_unknown.entry_" + (index + 1) + ".";
            output.append(prefix).append("scope=")
                    .append(safeFileToken(file.getName().replace(".unknown.properties", "")))
                    .append('\n')
                    .append(prefix).append("bytes=").append(file.length()).append('\n');
            Properties properties = new Properties();
            if (file.length() > MAX_UNKNOWN_PROPERTIES_BYTES) {
                output.append(prefix).append("parse_status=size_limit_exceeded\n");
                continue;
            }
            try (FileInputStream input = new FileInputStream(file)) {
                properties.load(input);
                output.append(prefix).append("operation=")
                        .append(safeFileToken(properties.getProperty("operation", "unknown")))
                        .append('\n')
                        .append(prefix).append("reboot_required=")
                        .append(safeBoolean(properties.getProperty("reboot_required")))
                        .append('\n')
                        .append(prefix).append("timestamp=")
                        .append(safeLong(properties.getProperty("timestamp")))
                        .append('\n')
                        .append(prefix).append("reason=")
                        .append(safeReason(properties.getProperty("reason", "unknown")))
                        .append('\n');
            } catch (IOException | RuntimeException error) {
                output.append(prefix).append("parse_status=invalid_or_unreadable\n");
            }
        }
        if (files.length > count) {
            output.append("root_unknown.omitted=").append(files.length - count).append('\n');
        }
    }

    private static void appendBackupSummary(StringBuilder output, Context context) {
        File backupRoot = new File(context.getFilesDir(), "browser_backups");
        List<BackupMetadata> backups = new ArrayList<>();
        File[] groups = backupRoot.listFiles(File::isDirectory);
        if (groups != null) {
            for (File group : groups) {
                if (Files.isSymbolicLink(group.toPath()) || !isSafeToken(group.getName())) {
                    continue;
                }
                File[] candidates = group.listFiles(File::isFile);
                if (candidates == null) {
                    continue;
                }
                for (File candidate : candidates) {
                    if (Files.isSymbolicLink(candidate.toPath())) {
                        continue;
                    }
                    backups.add(new BackupMetadata(
                            group.getName(), candidate.getName(), candidate.length(),
                            candidate.lastModified()));
                }
            }
        }
        backups.sort(Comparator.comparingLong(BackupMetadata::getModified).reversed());
        output.append("backups.total_count=").append(backups.size()).append('\n');
        int count = Math.min(backups.size(), MAX_BACKUP_ENTRIES);
        output.append("backups.listed_count=").append(count).append('\n');
        for (int index = 0; index < count; index++) {
            BackupMetadata backup = backups.get(index);
            String prefix = "backups.entry_" + (index + 1) + ".";
            output.append(prefix).append("group=")
                    .append(safeFileToken(backup.group)).append('\n')
                    .append(prefix).append("filename=")
                    .append(safeFileToken(backup.filename)).append('\n')
                    .append(prefix).append("bytes=").append(backup.bytes).append('\n')
                    .append(prefix).append("modified_epoch_ms=")
                    .append(backup.modified).append('\n');
        }
        if (backups.size() > count) {
            output.append("backups.omitted=").append(backups.size() - count).append('\n');
        }
    }

    private static byte[] buildRedactedSessionLog(
            LogHelper.SessionLogSnapshot snapshot, int outputLimit) throws IOException {
        byte[] tail = snapshot.getContents();
        String text = new String(tail, StandardCharsets.UTF_8);
        if (snapshot.isTruncatedAtStart()) {
            int firstLineBreak = text.indexOf('\n');
            text = firstLineBreak >= 0 ? text.substring(firstLineBreak + 1) : "";
        }

        ByteArrayOutputStream redacted = new ByteArrayOutputStream();
        String role = snapshot.isCurrentSession() ? "current" : "previous";
        writeLimited(redacted,
                ("# " + role + " modern process session; every line was redacted again.\n")
                        .getBytes(StandardCharsets.UTF_8), outputLimit);
        int lines = 0;
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines >= MAX_LOG_LINES) {
                    truncated = true;
                    break;
                }
                byte[] safe = (DiagnosticRedactor.redactLine(line) + "\n")
                        .getBytes(StandardCharsets.UTF_8);
                if (redacted.size() + safe.length > outputLimit) {
                    truncated = true;
                    break;
                }
                redacted.write(safe);
                lines++;
            }
        }
        if (truncated || snapshot.isTruncatedAtStart()) {
            writeLimited(redacted,
                    "[OLDER_OR_EXCESS_LOGS_OMITTED]\n".getBytes(StandardCharsets.UTF_8),
                    outputLimit);
        }
        return redacted.toByteArray();
    }

    private static void writeLimited(
            ByteArrayOutputStream output, byte[] value, int outputLimit) {
        int remaining = outputLimit - output.size();
        if (remaining > 0) {
            output.write(value, 0, Math.min(value.length, remaining));
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] contents)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(contents);
        zip.closeEntry();
    }

    private static void pruneExports(File directory, int keep) throws IOException {
        File[] temporaryFiles = directory.listFiles(file -> file.isFile()
                && file.getName().startsWith("BookmarkHelper-diagnostics-")
                && file.getName().endsWith(".tmp"));
        if (temporaryFiles != null) {
            for (File temporary : temporaryFiles) {
                if (!temporary.delete() && temporary.exists()) {
                    throw new IOException("Unable to clear stale diagnostic temporary file");
                }
            }
        }
        File[] exports = directory.listFiles(file -> file.isFile()
                && file.getName().startsWith("BookmarkHelper-diagnostics-")
                && file.getName().endsWith(".zip"));
        if (exports == null || exports.length <= keep) {
            return;
        }
        Arrays.sort(exports, Comparator.comparingLong(File::lastModified).reversed());
        for (int index = keep; index < exports.length; index++) {
            if (!exports[index].delete() && exports[index].exists()) {
                throw new IOException("Unable to enforce diagnostic archive count limit");
            }
        }
    }

    /** Clears session logs and only the dedicated FileProvider cache directory. */
    public static int clearDebugData(Context sourceContext) {
        DIAGNOSTIC_LOCK.lock();
        try {
            return LogHelper.clearAll() + clearCachedExportsLocked(sourceContext);
        } finally {
            DIAGNOSTIC_LOCK.unlock();
        }
    }

    private static int clearCachedExportsLocked(Context sourceContext) {
        File directory = new File(
                sourceContext.getApplicationContext().getCacheDir(), EXPORT_DIRECTORY);
        File[] files = directory.listFiles(File::isFile);
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (!file.getName().endsWith(".zip") && !file.getName().endsWith(".tmp")) {
                continue;
            }
            if (!file.delete() && file.exists()) {
                throw new IllegalStateException(
                        "Unable to delete cached diagnostic file: " + file.getName());
            }
            deleted++;
        }
        return deleted;
    }

    private static void moveAtomically(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String timestamp(long value, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(value));
    }

    private static String safePublicValue(String value) {
        if (value == null) {
            return "unknown";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", "?").trim();
        cleaned = DiagnosticRedactor.redactLine(cleaned);
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, 100) + "...";
    }

    private static String safeBoolean(String value) {
        return "true".equals(value) || "false".equals(value) ? value : "invalid";
    }

    private static String safeLong(String value) {
        if (value != null && value.matches("[0-9]{1,20}")) {
            return value;
        }
        return "invalid";
    }

    private static String safeReason(String value) {
        return DiagnosticRedactor.redactLine(value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }

    private static String safeFileToken(String value) {
        return isSafeToken(value) ? value : "<redacted-unsafe-name>";
    }

    private static boolean isSafeToken(String value) {
        return value != null && value.length() <= 160 && value.matches("[A-Za-z0-9._-]+");
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create private diagnostic export directory");
        }
    }

    private static final class BackupMetadata {
        private final String group;
        private final String filename;
        private final long bytes;
        private final long modified;

        private BackupMetadata(String group, String filename, long bytes, long modified) {
            this.group = group;
            this.filename = filename;
            this.bytes = bytes;
            this.modified = modified;
        }

        private long getModified() {
            return modified;
        }
    }
}
