package pro.kisscat.www.bookmarkhelper.sync.root;

import android.content.Context;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cross-thread/process single-flight guard plus a persistent fail-closed journal.
 *
 * <p>A PREPARED record is written before every Edge mutation. If the app dies,
 * the next import is blocked. A manual restore is allowed only after a reboot
 * when the previous root command may have survived the app process.</p>
 */
public final class RootTransactionGuard implements Closeable {
    private static final ReentrantLock PROCESS_LOCK = new ReentrantLock();
    private static final String BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id";
    private static final String UNKNOWN_VERSION = "1";

    private final String operation;
    private final String transactionId;
    private final File stateFile;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel channel;
    private final FileLock fileLock;
    private boolean closed;

    private RootTransactionGuard(
            String operation,
            File stateFile,
            RandomAccessFile randomAccessFile,
            FileChannel channel,
            FileLock fileLock) {
        this.operation = operation;
        this.transactionId = UUID.randomUUID().toString();
        this.stateFile = stateFile;
        this.randomAccessFile = randomAccessFile;
        this.channel = channel;
        this.fileLock = fileLock;
    }

    public static RootTransactionGuard acquire(Context context, String scope, String operation)
            throws IOException {
        requireSafeToken(scope, "事务范围");
        requireSafeToken(operation, "事务操作");
        if (!PROCESS_LOCK.tryLock()) {
            throw new IOException("另一个 Edge 数据事务正在运行，请等待其结束");
        }

        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        FileLock lock = null;
        try {
            File directory = new File(context.getFilesDir(), "root_transactions");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("无法创建 Root 事务目录");
            }
            File lockFile = new File(directory, scope + ".lock");
            randomAccessFile = new RandomAccessFile(lockFile, "rw");
            channel = randomAccessFile.getChannel();
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                lock = null;
            }
            if (lock == null) {
                throw new IOException("另一个 Edge 数据事务正在运行，请等待其结束");
            }
            return new RootTransactionGuard(
                    operation,
                    new File(directory, scope + ".unknown.properties"),
                    randomAccessFile,
                    channel,
                    lock);
        } catch (IOException | RuntimeException error) {
            closeQuietly(lock);
            closeQuietly(channel);
            closeQuietly(randomAccessFile);
            PROCESS_LOCK.unlock();
            throw error;
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public UnknownState readUnknownState() throws IOException {
        if (!stateFile.isFile()) {
            return null;
        }
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(stateFile)) {
            properties.load(input);
        }
        if (!UNKNOWN_VERSION.equals(properties.getProperty("version"))) {
            throw new IOException("Root 事务记录版本无效；为保护 Edge 数据，已禁止继续写入");
        }
        return UnknownState.from(properties);
    }

    public void requireNoUnknownState() throws IOException {
        UnknownState state = readUnknownState();
        if (state == null) {
            return;
        }
        throw new IOException("检测到未确认的 Edge 事务 " + state.getTransactionId()
                + "。为防止覆盖数据，已禁止继续写入；请按恢复提示处理");
    }

    public void requireRecoveryAllowed(UnknownState state) throws IOException {
        if (state == null || !state.isRebootRequired()) {
            return;
        }
        String currentBootId = readBootId();
        if (currentBootId.isEmpty() || state.getBootId().isEmpty()) {
            throw new IOException("无法确认设备是否已重启；请重启设备后再手动恢复 Edge 备份");
        }
        if (currentBootId.equals(state.getBootId())) {
            throw new IOException("上一次 Root 写命令可能仍在运行；请先重启设备，再手动恢复 Edge 备份");
        }
    }

    /** Write-ahead journal. A process death after this point is intentionally UNKNOWN. */
    public void prepareMutation(
            String targetPath,
            File recoveryBackup,
            RootFileMetadata expectedMetadata) throws IOException {
        UnknownState state = new UnknownState(
                transactionId,
                operation,
                System.currentTimeMillis(),
                readBootId(),
                true,
                targetPath,
                recoveryBackup == null ? "" : recoveryBackup.getAbsolutePath(),
                expectedMetadata,
                "PREPARED：若应用或 su 中断，必须先重启再恢复");
        writeState(state);
    }

    /** Retains the original recovery target while marking a new recovery attempt. */
    public void prepareRecoveryAttempt(UnknownState previous) throws IOException {
        UnknownState state = new UnknownState(
                transactionId,
                operation,
                System.currentTimeMillis(),
                readBootId(),
                true,
                previous.getTargetPath(),
                previous.getBackupPath(),
                previous.getExpectedMetadata(),
                "RECOVERY_PREPARED：恢复中断时必须再次重启");
        writeState(state);
    }

    public void markUnknown(
            String reason,
            boolean rebootRequired,
            String targetPath,
            File recoveryBackup,
            RootFileMetadata expectedMetadata) throws IOException {
        UnknownState state = new UnknownState(
                transactionId,
                operation,
                System.currentTimeMillis(),
                readBootId(),
                rebootRequired,
                targetPath,
                recoveryBackup == null ? "" : recoveryBackup.getAbsolutePath(),
                expectedMetadata,
                sanitizeReason(reason));
        writeState(state);
    }

    public void completeMutation() throws IOException {
        if (stateFile.exists() && !stateFile.delete()) {
            throw new IOException("Edge 文件已处理，但无法清除 Root UNKNOWN 事务记录");
        }
    }

    private void writeState(UnknownState state) throws IOException {
        Properties properties = state.toProperties();
        File parent = stateFile.getParentFile();
        File temporary = new File(parent,
                stateFile.getName() + "." + transactionId + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            properties.store(output, "BookmarkHelper fail-closed Root transaction");
            output.getFD().sync();
        }
        try {
            Files.move(
                    temporary.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(
                    temporary.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) {
                // The authoritative old state remains in place if replacement failed.
                temporary.delete();
            }
        }
    }

    private static String readBootId() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(BOOT_ID_PATH), StandardCharsets.US_ASCII))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "Root 文件状态无法确认";
        }
        String result = reason.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() <= 1000 ? result : result.substring(0, 1000);
    }

    private static void requireSafeToken(String value, String label) {
        if (value == null
                || !value.matches("[A-Za-z0-9_-]+")
                || ".".equals(value)
                || "..".equals(value)) {
            throw new IllegalArgumentException("非法" + label);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            fileLock.release();
        } catch (IOException error) {
            failure = error;
        }
        try {
            channel.close();
        } catch (IOException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        try {
            randomAccessFile.close();
        } catch (IOException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        } finally {
            PROCESS_LOCK.unlock();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Acquisition already failed; preserve the primary exception.
        }
    }

    private static void closeQuietly(FileLock lock) {
        if (lock == null) {
            return;
        }
        try {
            lock.release();
        } catch (IOException ignored) {
            // Acquisition already failed; preserve the primary exception.
        }
    }

    public static final class UnknownState {
        private final String transactionId;
        private final String operation;
        private final long timestamp;
        private final String bootId;
        private final boolean rebootRequired;
        private final String targetPath;
        private final String backupPath;
        private final RootFileMetadata expectedMetadata;
        private final String reason;

        private UnknownState(
                String transactionId,
                String operation,
                long timestamp,
                String bootId,
                boolean rebootRequired,
                String targetPath,
                String backupPath,
                RootFileMetadata expectedMetadata,
                String reason) {
            this.transactionId = transactionId;
            this.operation = operation;
            this.timestamp = timestamp;
            this.bootId = bootId == null ? "" : bootId;
            this.rebootRequired = rebootRequired;
            this.targetPath = targetPath == null ? "" : targetPath;
            this.backupPath = backupPath == null ? "" : backupPath;
            this.expectedMetadata = expectedMetadata;
            this.reason = sanitizeReason(reason);
        }

        private Properties toProperties() {
            Properties properties = new Properties();
            properties.setProperty("version", UNKNOWN_VERSION);
            properties.setProperty("transaction_id", transactionId);
            properties.setProperty("operation", operation);
            properties.setProperty("timestamp", Long.toString(timestamp));
            properties.setProperty("boot_id", bootId);
            properties.setProperty("reboot_required", Boolean.toString(rebootRequired));
            properties.setProperty("target_path", targetPath);
            properties.setProperty("backup_path", backupPath);
            properties.setProperty("reason", reason);
            if (expectedMetadata != null) {
                properties.setProperty("has_metadata", "true");
                expectedMetadata.put(properties, "metadata.");
            } else {
                properties.setProperty("has_metadata", "false");
            }
            return properties;
        }

        private static UnknownState from(Properties properties) throws IOException {
            String transactionId = required(properties, "transaction_id");
            String operation = required(properties, "operation");
            String timestampValue = required(properties, "timestamp");
            String rebootValue = required(properties, "reboot_required");
            long timestamp;
            try {
                timestamp = Long.parseLong(timestampValue);
            } catch (NumberFormatException error) {
                throw new IOException("Root 事务记录时间无效", error);
            }
            if (!"true".equals(rebootValue) && !"false".equals(rebootValue)) {
                throw new IOException("Root 事务记录的重启状态无效");
            }
            RootFileMetadata metadata = null;
            if ("true".equals(properties.getProperty("has_metadata"))) {
                metadata = RootFileMetadata.get(properties, "metadata.");
            }
            return new UnknownState(
                    transactionId,
                    operation,
                    timestamp,
                    properties.getProperty("boot_id", ""),
                    Boolean.parseBoolean(rebootValue),
                    properties.getProperty("target_path", ""),
                    properties.getProperty("backup_path", ""),
                    metadata,
                    properties.getProperty("reason", "未知原因"));
        }

        private static String required(Properties properties, String key) throws IOException {
            String value = properties.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                throw new IOException("Root 事务记录缺少 " + key);
            }
            return value;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getOperation() {
            return operation;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getBootId() {
            return bootId;
        }

        public boolean isRebootRequired() {
            return rebootRequired;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public String getBackupPath() {
            return backupPath;
        }

        public RootFileMetadata getExpectedMetadata() {
            return expectedMetadata;
        }

        public String getReason() {
            return reason;
        }
    }
}
