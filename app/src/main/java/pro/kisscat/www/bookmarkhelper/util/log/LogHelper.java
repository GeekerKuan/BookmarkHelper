package pro.kisscat.www.bookmarkhelper.util.log;

import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import pro.kisscat.www.bookmarkhelper.common.shared.MetaData;
import pro.kisscat.www.bookmarkhelper.diagnostics.DiagnosticLogPolicy;
import pro.kisscat.www.bookmarkhelper.diagnostics.DiagnosticRedactor;
import pro.kisscat.www.bookmarkhelper.entry.log.LogEntry;
import pro.kisscat.www.bookmarkhelper.util.context.ContextUtil;

/** App-private, size-bounded, privacy-filtered session logging. */
public final class LogHelper {
    private static final String LOG_DIR = "logs";
    private static final int MAX_RETAINED_SESSION_FILES = 5;
    private static final int MAX_QUEUED_ENTRIES = 2_000;
    private static final int MAX_EXCEPTION_CAUSES = 4;
    private static final int MAX_STACK_FRAMES_PER_CAUSE = 24;
    private static final long MAX_SESSION_FILE_BYTES = 768L * 1_024L;
    private static final byte[] ROTATION_MARKER =
            "[EARLIER_SESSION_LOGS_DISCARDED_BY_SIZE_LIMIT]\n"
                    .getBytes(StandardCharsets.UTF_8);

    private static final ConcurrentLinkedQueue<LogEntry> LOG_QUEUE =
            new ConcurrentLinkedQueue<>();
    private static final SimpleDateFormat LOG_TIMESTAMP =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat SESSION_FILENAME =
            new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US);

    private static volatile boolean initialized;
    private static File logDirectory;
    private static File currentSessionLogFile;

    private LogHelper() {
    }

    public static void v(String message) {
        v(MetaData.LOG_V_DEFAULT, message);
    }

    public static void v(String tag, Object message) {
        v(tag, String.valueOf(message), true);
    }

    public static void v(String message, boolean trim) {
        v(MetaData.LOG_V_DEFAULT, message, trim);
    }

    public static void v(String tag, String text, boolean trim) {
        // All build variants use the same private file pipeline. Exported logs
        // therefore never depend on logcat/System.out and always get sanitized.
        log(tag, text, 'v');
    }

    public static void w(Object message) {
        log(MetaData.LOG_W_DEFAULT, String.valueOf(message), 'w');
    }

    public static void w(String tag, Object message) {
        log(tag, String.valueOf(message), 'w');
    }

    public static void w(String tag, String text) {
        log(tag, text, 'w');
    }

    public static void e(Throwable throwable) {
        e(MetaData.LOG_E_DEFAULT, throwable);
    }

    public static void e(String tag, Throwable throwable) {
        log(tag, printException(throwable), 'e');
    }

    public static void e(String tag, String text) {
        log(tag, text, 'e');
    }

    public static void e(String text) {
        e(MetaData.LOG_E_DEFAULT, text);
    }

    public static void d(String tag, Object message) {
        log(tag, String.valueOf(message), 'd');
    }

    public static void d(String tag, String text) {
        log(tag, text, 'd');
    }

    public static void i(String tag, Object message) {
        log(tag, String.valueOf(message), 'i');
    }

    public static void i(String tag, String text) {
        log(tag, text, 'i');
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        logDirectory = new File(ContextUtil.getApplicationContext().getFilesDir(), LOG_DIR);
        if (!logDirectory.isDirectory() && !logDirectory.mkdirs()) {
            throw new IllegalArgumentException("无法创建应用私有日志目录");
        }
        String fileName = "session-" + SESSION_FILENAME.format(new Date())
                + "-p" + Process.myPid() + ".log";
        currentSessionLogFile = new File(logDirectory, fileName);
        try {
            if (!currentSessionLogFile.isFile() && !currentSessionLogFile.createNewFile()) {
                throw new IOException("createNewFile returned false");
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("无法创建应用私有日志文件", error);
        }
        initialized = true;
        pruneOldSessionFiles();
    }

    public static void write() {
        if (!LOG_QUEUE.isEmpty()) {
            WriteThread.schedule();
        }
    }

    /** Best-effort synchronous flush for crash capture and diagnostic export. */
    public static void writeNow() {
        flush();
    }

    /** Deletes all private session logs without touching backups or diagnostics. */
    public static synchronized int clearAll() {
        ensureInitialized();
        LOG_QUEUE.clear();
        int deleted = 0;
        File[] files = logDirectory.listFiles(File::isFile);
        if (files != null) {
            for (File file : files) {
                if (!file.delete() && file.exists()) {
                    throw new IllegalStateException(
                            "无法删除应用私有日志文件：" + file.getName());
                }
                deleted++;
            }
        }
        try {
            if (!currentSessionLogFile.createNewFile() && !currentSessionLogFile.isFile()) {
                throw new IOException("createNewFile returned false");
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法重新创建当前会话日志", error);
        }
        return deleted;
    }

    /**
     * Takes a bounded tail snapshot under the same monitor as flush/clear.
     * Only the current and at most one previous privacy-filtered modern session
     * can be exposed; legacy log names never enter the diagnostic exporter.
     */
    public static synchronized List<SessionLogSnapshot> snapshotRecentSessions(
            int maximumTotalBytes, int maximumFiles) throws IOException {
        if (maximumTotalBytes <= 0 || maximumFiles <= 0 || maximumFiles > 2) {
            throw new IllegalArgumentException("Invalid diagnostic session snapshot limit");
        }
        flush();
        ensureInitialized();

        List<File> selected = new ArrayList<>();
        selected.add(currentSessionLogFile);
        File[] candidates = logDirectory.listFiles(file -> file.isFile()
                && !Files.isSymbolicLink(file.toPath())
                && DiagnosticLogPolicy.isModernSessionLogName(file.getName())
                && !file.equals(currentSessionLogFile));
        if (candidates != null && maximumFiles > 1) {
            Arrays.sort(candidates, Comparator.comparingLong(File::lastModified).reversed());
            for (File candidate : candidates) {
                if (selected.size() >= maximumFiles) {
                    break;
                }
                selected.add(candidate);
            }
        }

        List<SessionLogSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            File file = selected.get(index);
            long length = file.length();
            int fileLimit;
            if (selected.size() == 1) {
                fileLimit = maximumTotalBytes;
            } else if (index == 0) {
                // Preserve at least half of the total allowance for the prior
                // crash session; unused current-session space flows to it.
                fileLimit = (int) Math.min(length, maximumTotalBytes / 2);
            } else {
                fileLimit = maximumTotalBytes - snapshots.get(0).contents.length;
            }
            int count = (int) Math.min(length, fileLimit);
            byte[] contents = new byte[count];
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                input.seek(Math.max(0L, length - count));
                input.readFully(contents);
            }
            snapshots.add(new SessionLogSnapshot(contents, length > count, index == 0));
        }
        return snapshots;
    }

    static boolean hasPendingEntries() {
        return !LOG_QUEUE.isEmpty();
    }

    static synchronized void flush() {
        if (LOG_QUEUE.isEmpty()) {
            return;
        }
        ensureInitialized();
        FileOutputStream output = null;
        long written = currentSessionLogFile.length();
        try {
            output = new FileOutputStream(currentSessionLogFile, true);
            LogEntry entry;
            while ((entry = LOG_QUEUE.poll()) != null) {
                String line = LOG_TIMESTAMP.format(entry.getTime())
                        + "    " + entry.getLevel()
                        + "    " + DiagnosticRedactor.redactLine(entry.getTag())
                        + "    " + DiagnosticRedactor.redactLine(entry.getText())
                        + "\n";
                byte[] encoded = line.getBytes(StandardCharsets.UTF_8);
                if (written + encoded.length > MAX_SESSION_FILE_BYTES) {
                    output.close();
                    output = new FileOutputStream(currentSessionLogFile, false);
                    output.write(ROTATION_MARKER);
                    written = ROTATION_MARKER.length;
                }
                output.write(encoded);
                written += encoded.length;
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException error) {
            // Do not recursively log a logger failure or spill it into logcat.
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                    // Nothing more can be recovered here.
                }
            }
        }
    }

    private static void log(String tag, String text, char level) {
        String safeTag = DiagnosticRedactor.redactLine(tag == null ? "" : tag);
        String safeText = DiagnosticRedactor.redactLine(text == null ? "" : text);
        while (LOG_QUEUE.size() >= MAX_QUEUED_ENTRIES) {
            LOG_QUEUE.poll();
        }
        LOG_QUEUE.add(new LogEntry(String.valueOf(level), safeTag, safeText));
        write();
    }

    private static String printException(Throwable throwable) {
        if (throwable == null) {
            return "null throwable";
        }
        StringBuilder summary = new StringBuilder();
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable current = throwable;
        int causes = 0;
        while (current != null && causes < MAX_EXCEPTION_CAUSES
                && seen.put(current, Boolean.TRUE) == null) {
            if (causes > 0) {
                summary.append("Caused by: ");
            }
            // Throwable messages are untrusted and often contain SQLite rows,
            // URLs, titles, tokens or paths. Only structural crash data is kept.
            summary.append(current.getClass().getName()).append('\n');
            StackTraceElement[] stack = current.getStackTrace();
            int frameCount = Math.min(stack.length, MAX_STACK_FRAMES_PER_CAUSE);
            for (int index = 0; index < frameCount; index++) {
                summary.append("    at ").append(stack[index]).append('\n');
            }
            if (stack.length > frameCount) {
                summary.append("    ... ").append(stack.length - frameCount)
                        .append(" frames omitted\n");
            }
            current = current.getCause();
            causes++;
        }
        if (current != null) {
            summary.append("... additional causes omitted\n");
        }
        return summary.toString();
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    private static void pruneOldSessionFiles() {
        File[] files = logDirectory.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        List<File> previousSessions = new ArrayList<>();
        for (File file : files) {
            if (!DiagnosticLogPolicy.isModernSessionLogName(file.getName())) {
                // Legacy *Log.txt files may predate privacy filtering. They are
                // never exported and are removed during the migration when possible.
                file.delete();
            } else if (!file.equals(currentSessionLogFile)) {
                previousSessions.add(file);
            }
        }
        previousSessions.sort(Comparator.comparingLong(File::lastModified).reversed());
        int retainedPrevious = Math.max(0, MAX_RETAINED_SESSION_FILES - 1);
        for (int index = retainedPrevious; index < previousSessions.size(); index++) {
            previousSessions.get(index).delete();
        }
    }

    public static final class SessionLogSnapshot {
        private final byte[] contents;
        private final boolean truncatedAtStart;
        private final boolean currentSession;

        private SessionLogSnapshot(
                byte[] contents, boolean truncatedAtStart, boolean currentSession) {
            this.contents = contents;
            this.truncatedAtStart = truncatedAtStart;
            this.currentSession = currentSession;
        }

        public byte[] getContents() {
            return contents.clone();
        }

        public boolean isTruncatedAtStart() {
            return truncatedAtStart;
        }

        public boolean isCurrentSession() {
            return currentSession;
        }
    }
}
