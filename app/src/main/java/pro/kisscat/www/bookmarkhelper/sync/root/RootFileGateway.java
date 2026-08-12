package pro.kisscat.www.bookmarkhelper.sync.root;

import android.content.Context;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Root-only file operations with app-private snapshots and recoverable writes. */
public final class RootFileGateway {
    private static final long VERIFY_TIMEOUT_SECONDS = 15L;
    // Mirrors Android's hidden UserHandle.PER_USER_RANGE.
    private static final int PER_USER_RANGE = 100000;

    private final Context context;

    public RootFileGateway(Context context) {
        this.context = context.getApplicationContext();
    }

    public void requireRoot() throws IOException {
        if (!RootShell.isAvailable()) {
            throw new IOException("未获得 Root 权限，请在 Root 管理器中允许书签助手使用 su");
        }
    }

    /** Compatibility overload for source-browser snapshots. */
    public void forceStop(String packageName) throws IOException {
        forceStop(packageName, currentUserId());
    }

    /**
     * Force-stops the package for the app's explicit Android user and verifies
     * every process whose cmdline is either the package or package:*.
     */
    public void forceStop(String packageName, int userId) throws IOException {
        requireSafePackageName(packageName);
        requireSafeUserId(userId);
        String scan = buildPackageProcessScan(packageName, userId);
        // stop-app does not put the package into the stopped state. Modern Via
        // can be relaunched immediately by its background components, making a
        // consistent SQLite snapshot impossible. The UI explicitly tells users
        // that Via must be reopened, so force-stop is the correct transaction
        // boundary on every supported Android 12-16 release.
        String command = "am force-stop --user " + userId + " " + packageName
                + " >/dev/null 2>&1; i=0; while [ $i -lt 10 ]; do "
                + scan
                + "; [ \"$bookmarkhelper_found\" -eq 0 ] && exit 0"
                + "; sleep 1; i=$((i+1)); done; exit 4";
        RootShell.Result result = RootShell.run(20L, command);
        requireSuccess(result, "停止 " + packageName + " 的全部进程失败");
    }

    public String resolveFirstExistingFile(String... candidates) throws IOException {
        if (candidates == null || candidates.length == 0) {
            throw new IOException("没有提供候选文件路径");
        }
        StringBuilder script = new StringBuilder();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            script.append("if [ -f ")
                    .append(RootShell.quote(candidate))
                    .append(" ]; then printf '__BOOKMARKHELPER_PATH__%s\\n' ")
                    .append(RootShell.quote(candidate))
                    .append("; exit 0; fi; ");
        }
        script.append("exit 3");
        RootShell.Result result = RootShell.run(script.toString());
        if (!result.isSuccess() && result.getExitCode() == 3 && !result.isTimedOut()) {
            return null;
        }
        requireSuccess(result, "探测浏览器数据文件失败");
        for (String line : result.getStdoutLines()) {
            if (line != null && line.startsWith("__BOOKMARKHELPER_PATH__")) {
                return line.substring("__BOOKMARKHELPER_PATH__".length());
            }
        }
        return null;
    }

    public String resolveFirstExistingDirectory(String... candidates) throws IOException {
        if (candidates == null || candidates.length == 0) {
            throw new IOException("没有提供候选目录路径");
        }
        StringBuilder script = new StringBuilder();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            script.append("if [ -d ")
                    .append(RootShell.quote(candidate))
                    .append(" ]; then printf '__BOOKMARKHELPER_PATH__%s\\n' ")
                    .append(RootShell.quote(candidate))
                    .append("; exit 0; fi; ");
        }
        script.append("exit 3");
        RootShell.Result result = RootShell.run(script.toString());
        if (!result.isSuccess() && result.getExitCode() == 3 && !result.isTimedOut()) return null;
        requireSuccess(result, "探测浏览器数据目录失败");
        for (String line : result.getStdoutLines()) {
            if (line != null && line.startsWith("__BOOKMARKHELPER_PATH__")) {
                return line.substring("__BOOKMARKHELPER_PATH__".length());
            }
        }
        return null;
    }

    /** Finds a small, bounded set of files below a browser-private directory. */
    public List<String> findFilesByNamePattern(
            String baseDir, String namePattern, int maxDepth, int maxResults) throws IOException {
        if (!isSafeBrowserPrivatePath(baseDir)) {
            throw new IllegalArgumentException("非法浏览器数据目录");
        }
        if (namePattern == null || !namePattern.matches("[A-Za-z0-9._*?\\[\\]-]+")) {
            throw new IllegalArgumentException("非法文件匹配模式");
        }
        if (maxDepth < 1 || maxDepth > 8 || maxResults < 1 || maxResults > 128) {
            throw new IllegalArgumentException("非法扫描范围");
        }
        String command = "if [ -d " + RootShell.quote(baseDir) + " ]; then find "
                + RootShell.quote(baseDir) + " -maxdepth " + maxDepth
                + " -type f -name " + RootShell.quote(namePattern)
                + " 2>/dev/null | head -n " + maxResults + "; fi";
        RootShell.Result result = RootShell.run(45L, command);
        requireSuccess(result, "扫描浏览器标签页数据失败");
        return result.getStdoutLines();
    }

    /**
     * Android 15+ may expose credential-encrypted app data through /data_mirror
     * even when the traditional /data/user link is unavailable to the selected
     * root implementation.  Keep the scanner constrained to Android's private
     * data roots while accepting both representations.
     */
    static boolean isSafeBrowserPrivatePath(String path) {
        if (path == null || path.isEmpty()
                || path.indexOf('\0') >= 0
                || path.indexOf('\r') >= 0
                || path.indexOf('\n') >= 0
                || path.contains("/../")
                || path.endsWith("/..")) {
            return false;
        }
        return path.startsWith("/data/") || path.startsWith("/data_mirror/");
    }

    public long lastModifiedMillis(String path) throws IOException {
        RootShell.Result result = RootShell.run(
                "set -e; test -f " + RootShell.quote(path)
                        + "; printf '__BOOKMARKHELPER_MTIME__%s\\n' \"$(stat -c %Y "
                        + RootShell.quote(path) + ")\"");
        requireSuccess(result, "读取浏览器标签页时间失败");
        for (String line : result.getStdoutLines()) {
            if (line != null && line.startsWith("__BOOKMARKHELPER_MTIME__")) {
                try {
                    return Math.multiplyExact(
                            Long.parseLong(line.substring("__BOOKMARKHELPER_MTIME__".length())),
                            1000L);
                } catch (ArithmeticException | NumberFormatException error) {
                    throw new IOException("浏览器标签页时间无效", error);
                }
            }
        }
        throw new IOException("浏览器标签页时间缺失");
    }

    public String findFirstNamedFile(String baseDir, String fileName) throws IOException {
        String command = "if [ -d " + RootShell.quote(baseDir) + " ]; then find "
                + RootShell.quote(baseDir) + " -type f -name " + RootShell.quote(fileName)
                + " 2>/dev/null | head -n 1; fi";
        RootShell.Result result = RootShell.run(45L, command);
        requireSuccess(result, "扫描浏览器数据目录失败");
        List<String> lines = result.getStdoutLines();
        return lines.isEmpty() ? null : lines.get(0).trim();
    }

    /** Compatibility overload for callers that use their own Android user. */
    public void requireStopped(String packageName) throws IOException {
        requireStopped(packageName, currentUserId());
    }

    public void requireStopped(String packageName, int userId) throws IOException {
        requireSafePackageName(packageName);
        requireSafeUserId(userId);
        RootShell.Result result = RootShell.run(
                buildPackageProcessScan(packageName, userId)
                        + "; [ \"$bookmarkhelper_found\" -eq 0 ]");
        requireSuccess(result, packageName + " 在同步期间重新启动，已取消写入");
    }

    public void requireFileUnchanged(String targetPath, File snapshot) throws IOException {
        if (snapshot == null || !snapshot.isFile() || snapshot.length() == 0L) {
            throw new IOException("浏览器数据快照无效");
        }
        RootShell.Result result = RootShell.run(
                "cmp -s " + RootShell.quote(targetPath) + " "
                        + RootShell.quote(snapshot.getAbsolutePath()));
        requireSuccess(result, "Edge 收藏夹在同步期间发生变化，已取消写入");
    }

    public RootFileMetadata captureMetadata(String targetPath) throws IOException {
        return readMetadata(targetPath);
    }

    public File snapshotFile(String sourcePath, String group, String destinationName)
            throws IOException {
        File destinationDir = workingDirectory(group);
        File destination = new File(destinationDir, safeName(destinationName));
        if (destination.exists() && !destination.delete()) {
            throw new IOException("无法清理旧快照：" + destination.getAbsolutePath());
        }
        copyReadable(sourcePath, destination);
        if (!destination.isFile() || destination.length() == 0L) {
            throw new IOException("浏览器数据快照为空：" + sourcePath);
        }
        return destination;
    }

    public File snapshotSqliteDatabase(String sourcePath, String group, String destinationName)
            throws IOException {
        File main = snapshotFile(sourcePath, group, destinationName);
        snapshotOptionalSibling(sourcePath + "-wal", new File(main.getAbsolutePath() + "-wal"));
        snapshotOptionalSibling(sourcePath + "-shm", new File(main.getAbsolutePath() + "-shm"));
        snapshotOptionalSibling(
                sourcePath + "-journal",
                new File(main.getAbsolutePath() + "-journal"));
        return main;
    }

    public File backupFile(String sourcePath, String group) throws IOException {
        File backupDir = new File(context.getFilesDir(), "browser_backups/" + safeName(group));
        ensureDirectory(backupDir);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        File backup = new File(backupDir, "Bookmarks-" + timestamp + "-" + nonce + ".bak");
        copyReadable(sourcePath, backup);
        if (!backup.isFile() || backup.length() == 0L) {
            throw new IOException("Edge 备份创建失败");
        }
        return backup;
    }

    public File latestBackup(String group) throws IOException {
        File backupDir = new File(context.getFilesDir(), "browser_backups/" + safeName(group));
        if (!backupDir.isDirectory()) {
            return null;
        }
        File[] candidates = backupDir.listFiles();
        if (candidates == null) {
            throw new IOException("无法读取 Edge 备份目录");
        }
        File latest = null;
        for (File candidate : candidates) {
            if (!candidate.isFile() || candidate.length() == 0L) {
                continue;
            }
            if (latest == null || candidate.lastModified() > latest.lastModified()) {
                latest = candidate;
            }
        }
        return latest;
    }

    /**
     * Compatibility overload. Edge should use the transaction-aware overload.
     */
    public void replaceContentsPreservingMetadata(
            String targetPath,
            File verifiedSource,
            File backup) throws IOException {
        replaceContentsPreservingMetadata(
                targetPath,
                verifiedSource,
                backup,
                UUID.randomUUID().toString(),
                null,
                null,
                -1);
    }

    /**
     * Atomically installs a verified file beside the target. The command checks
     * that every target-package process is stopped immediately before copying
     * and again before rename. UID, GID, mode and SELinux/MCS context are
     * recorded and verified after both replacement and rollback.
     *
     * <p>On a root-command timeout or unverifiable rollback this method throws
     * {@link UncertainRootStateException}, deliberately leaves unique temporary
     * files in place, and relies on the caller's persistent UNKNOWN journal.</p>
     */
    public void replaceContentsPreservingMetadata(
            String targetPath,
            File verifiedSource,
            File backup,
            String transactionId,
            RootFileMetadata expectedMetadata,
            String stoppedPackageName,
            int userId) throws IOException {
        if (verifiedSource == null || !verifiedSource.isFile() || verifiedSource.length() == 0L) {
            throw new IOException("拒绝写入空的 Edge 书签文件");
        }
        String safeTransactionId = safeTransactionId(transactionId);
        if (stoppedPackageName != null) {
            requireSafePackageName(stoppedPackageName);
            requireSafeUserId(userId);
        }
        RootFileMetadata originalMetadata = expectedMetadata == null
                ? captureMetadata(targetPath)
                : expectedMetadata;
        String temporaryPath = targetPath + ".bookmarkhelper." + safeTransactionId + ".tmp";
        String write = "set -e; rm -f " + RootShell.quote(temporaryPath)
                + "; test -s " + RootShell.quote(verifiedSource.getAbsolutePath())
                + "; test -f " + RootShell.quote(targetPath)
                + "; test ! -L " + RootShell.quote(targetPath)
                + packageStoppedAssertion(stoppedPackageName, userId)
                + "; cp --preserve=all " + RootShell.quote(targetPath) + " "
                + RootShell.quote(temporaryPath)
                + "; cat " + RootShell.quote(verifiedSource.getAbsolutePath())
                + " > " + RootShell.quote(temporaryPath)
                + "; " + applyMetadataCommand(temporaryPath, originalMetadata)
                + "; sync; test -s " + RootShell.quote(temporaryPath)
                + "; cmp -s " + RootShell.quote(temporaryPath) + " "
                + RootShell.quote(verifiedSource.getAbsolutePath())
                + packageStoppedAssertion(stoppedPackageName, userId)
                + "; mv -f " + RootShell.quote(temporaryPath) + " "
                + RootShell.quote(targetPath)
                + "; sync; cmp -s " + RootShell.quote(targetPath) + " "
                + RootShell.quote(verifiedSource.getAbsolutePath());

        RootShell.Result writeResult = RootShell.run(45L, write);
        if (writeResult.isTimedOut()) {
            throw new UncertainRootStateException(
                    "Edge Root 写命令超时，子进程可能仍在运行；已保留事务现场",
                    true,
                    originalMetadata);
        }

        if (writeResult.isSuccess()) {
            try {
                verifyKnownState(targetPath, verifiedSource, originalMetadata);
                cleanupKnownTemporaryFile(temporaryPath);
                return;
            } catch (UncertainRootStateException uncertain) {
                throw uncertain;
            } catch (IOException verificationFailure) {
                rollbackOrThrow(
                        targetPath,
                        backup,
                        originalMetadata,
                        safeTransactionId,
                        temporaryPath,
                        stoppedPackageName,
                        userId,
                        verificationFailure.getMessage());
                return;
            }
        }

        MatchResult originalState = matchKnownState(targetPath, backup, originalMetadata);
        if (originalState == MatchResult.UNKNOWN) {
            throw new UncertainRootStateException(
                    "Edge 写入失败后无法确认原文件状态；已保留事务现场",
                    false,
                    originalMetadata);
        }
        if (originalState == MatchResult.MATCH) {
            cleanupKnownTemporaryFile(temporaryPath);
            throw new IOException("Edge 写入失败，原文件及元数据未发生变化："
                    + writeResult.describeFailure());
        }

        rollbackOrThrow(
                targetPath,
                backup,
                originalMetadata,
                safeTransactionId,
                temporaryPath,
                stoppedPackageName,
                userId,
                writeResult.describeFailure());
    }

    private void rollbackOrThrow(
            String targetPath,
            File backup,
            RootFileMetadata expectedMetadata,
            String transactionId,
            String writeTemporaryPath,
            String stoppedPackageName,
            int userId,
            String originalFailure) throws IOException {
        if (backup == null || !backup.isFile() || backup.length() == 0L) {
            throw new UncertainRootStateException(
                    "Edge 状态发生变化且没有可用备份；已保留事务现场",
                    false,
                    expectedMetadata);
        }

        String restorePath = targetPath + ".bookmarkhelper." + transactionId + ".restore.tmp";
        String restore = "set -e; rm -f " + RootShell.quote(restorePath)
                + "; test -s " + RootShell.quote(backup.getAbsolutePath())
                + "; test ! -L " + RootShell.quote(targetPath)
                + packageStoppedAssertion(stoppedPackageName, userId)
                + "; if [ -f " + RootShell.quote(targetPath) + " ]; then cp --preserve=all "
                + RootShell.quote(targetPath) + " " + RootShell.quote(restorePath)
                + "; else cp --preserve=all " + RootShell.quote(backup.getAbsolutePath()) + " "
                + RootShell.quote(restorePath) + "; fi"
                + "; cat " + RootShell.quote(backup.getAbsolutePath()) + " > "
                + RootShell.quote(restorePath)
                + "; " + applyMetadataCommand(restorePath, expectedMetadata)
                + "; sync; cmp -s " + RootShell.quote(restorePath) + " "
                + RootShell.quote(backup.getAbsolutePath())
                + packageStoppedAssertion(stoppedPackageName, userId)
                + "; mv -f " + RootShell.quote(restorePath) + " "
                + RootShell.quote(targetPath)
                + "; sync; cmp -s " + RootShell.quote(targetPath) + " "
                + RootShell.quote(backup.getAbsolutePath());
        RootShell.Result restoreResult = RootShell.run(45L, restore);
        if (restoreResult.isTimedOut()) {
            throw new UncertainRootStateException(
                    "Edge 自动恢复超时，Root 子进程可能仍在运行；已保留事务现场",
                    true,
                    expectedMetadata);
        }
        if (!restoreResult.isSuccess()) {
            throw new UncertainRootStateException(
                    "Edge 自动恢复失败；已保留事务现场：" + restoreResult.describeFailure(),
                    false,
                    expectedMetadata);
        }

        MatchResult restored = matchKnownState(targetPath, backup, expectedMetadata);
        if (restored != MatchResult.MATCH) {
            throw new UncertainRootStateException(
                    restored == MatchResult.UNKNOWN
                            ? "Edge 自动恢复后无法完成验证；已保留事务现场"
                            : "Edge 自动恢复后的内容或元数据不一致；已保留事务现场",
                    false,
                    expectedMetadata);
        }

        cleanupKnownTemporaryFile(writeTemporaryPath);
        cleanupKnownTemporaryFile(restorePath);
        throw new IOException("Edge 写入失败，已恢复并验证原文件：" + originalFailure);
    }

    private void verifyKnownState(
            String targetPath,
            File expectedContents,
            RootFileMetadata expectedMetadata) throws IOException {
        MatchResult result = matchKnownState(targetPath, expectedContents, expectedMetadata);
        if (result == MatchResult.UNKNOWN) {
            throw new UncertainRootStateException(
                    "Edge 写入完成后验证超时，文件状态无法确认",
                    false,
                    expectedMetadata);
        }
        if (result != MatchResult.MATCH) {
            throw new IOException("Edge 写入后的内容或 UID/GID/mode/SELinux 元数据不一致");
        }
    }

    private MatchResult matchKnownState(
            String targetPath,
            File expectedContents,
            RootFileMetadata expectedMetadata) {
        if (expectedContents == null || !expectedContents.isFile()) {
            return MatchResult.MISMATCH;
        }
        RootShell.Result content = RootShell.run(
                VERIFY_TIMEOUT_SECONDS,
                "test -f " + RootShell.quote(targetPath)
                        + "; test ! -L " + RootShell.quote(targetPath)
                        + "; cmp -s " + RootShell.quote(targetPath) + " "
                        + RootShell.quote(expectedContents.getAbsolutePath()));
        if (content.isTimedOut()) {
            return MatchResult.UNKNOWN;
        }
        if (!content.isSuccess()) {
            return MatchResult.MISMATCH;
        }
        try {
            return expectedMetadata.equals(readMetadata(targetPath))
                    ? MatchResult.MATCH
                    : MatchResult.MISMATCH;
        } catch (CommandFailure failure) {
            return failure.isTimedOut() ? MatchResult.UNKNOWN : MatchResult.MISMATCH;
        } catch (IOException failure) {
            return MatchResult.MISMATCH;
        }
    }

    private RootFileMetadata readMetadata(String targetPath) throws IOException {
        String quotedPath = RootShell.quote(targetPath);
        String command = "set -e; test -f " + quotedPath
                + "; test ! -L " + quotedPath
                + "; bookmarkhelper_stat=$(stat -c '%u|%g|%a' " + quotedPath + ")"
                + "; bookmarkhelper_label_line=$(ls -Zd " + quotedPath + ")"
                + "; bookmarkhelper_label=${bookmarkhelper_label_line%% *}"
                + "; case \"$bookmarkhelper_label\" in *:*) ;; *) exit 12;; esac"
                + "; printf '" + RootFileMetadata.OUTPUT_MARKER
                + "%s|%s\\n' \"$bookmarkhelper_stat\" \"$bookmarkhelper_label\"";
        RootShell.Result result = RootShell.run(VERIFY_TIMEOUT_SECONDS, command);
        if (!result.isSuccess()) {
            throw new CommandFailure(
                    "读取 Edge 文件元数据失败：" + result.describeFailure(),
                    result.isTimedOut());
        }
        return RootFileMetadata.fromRootOutput(result.getStdoutLines());
    }

    private static String applyMetadataCommand(String path, RootFileMetadata metadata) {
        return "chown " + metadata.getUid() + ":" + metadata.getGid() + " "
                + RootShell.quote(path)
                + "; chmod " + metadata.getMode() + " " + RootShell.quote(path)
                + "; chcon " + RootShell.quote(metadata.getSelinuxContext()) + " "
                + RootShell.quote(path);
    }

    private static String packageStoppedAssertion(String packageName, int userId) {
        if (packageName == null) {
            return "";
        }
        return "; " + buildPackageProcessScan(packageName, userId)
                + "; [ \"$bookmarkhelper_found\" -eq 0 ] || exit 21";
    }

    private static String buildPackageProcessScan(String packageName, int userId) {
        // Android browser child processes normally use package:suffix argv[0].
        // Reading /proc cmdline catches them while pidof(package) does not. UID
        // range filtering avoids blocking on the same package in another user.
        return "bookmarkhelper_found=0; for bookmarkhelper_proc in /proc/[0-9]*; do "
                + "[ -r \"$bookmarkhelper_proc/cmdline\" ] "
                + "&& [ -r \"$bookmarkhelper_proc/status\" ] || continue; "
                + "bookmarkhelper_uid=$(awk '/^Uid:/{print $2; exit}' "
                + "\"$bookmarkhelper_proc/status\" 2>/dev/null); "
                + "[ -n \"$bookmarkhelper_uid\" ] || continue; "
                + "[ $((bookmarkhelper_uid / " + PER_USER_RANGE + ")) -eq "
                + userId + " ] || continue; "
                + "bookmarkhelper_cmd=$(tr '\\000' '\\n' < \"$bookmarkhelper_proc/cmdline\" "
                + "2>/dev/null | head -n 1); case \"$bookmarkhelper_cmd\" in "
                + packageName + "|" + packageName
                + ":*) bookmarkhelper_found=1; break;; esac; done";
    }

    private static void cleanupKnownTemporaryFile(String path) {
        RootShell.run("rm -f " + RootShell.quote(path));
    }

    public File workingDirectory(String group) throws IOException {
        File directory = new File(context.getCacheDir(), "browser_sync/" + safeName(group));
        ensureDirectory(directory);
        return directory;
    }

    private void snapshotOptionalSibling(String sourcePath, File destination) throws IOException {
        if (destination.exists() && !destination.delete()) {
            throw new IOException("无法清理旧 SQLite 快照：" + destination.getAbsolutePath());
        }
        createPrivateDestination(destination);
        String script = "if [ -f " + RootShell.quote(sourcePath) + " ]; then cat "
                + RootShell.quote(sourcePath) + " > "
                + RootShell.quote(destination.getAbsolutePath())
                + "; cmp -s " + RootShell.quote(sourcePath) + " "
                + RootShell.quote(destination.getAbsolutePath()) + "; fi";
        RootShell.Result result = RootShell.run(script);
        requireSuccess(result, "SQLite 附属文件快照失败");
        if (destination.length() > 0L) syncPrivateFile(destination);
        if (destination.length() == 0L && !destination.delete()) {
            throw new IOException("无法清理空的 SQLite 快照：" + destination.getAbsolutePath());
        }
    }

    private void copyReadable(String sourcePath, File destination) throws IOException {
        ensureDirectory(destination.getParentFile());
        createPrivateDestination(destination);
        String command = "set -e; test -f " + RootShell.quote(sourcePath)
                + "; cat " + RootShell.quote(sourcePath) + " > "
                + RootShell.quote(destination.getAbsolutePath())
                + "; cmp -s " + RootShell.quote(sourcePath) + " "
                + RootShell.quote(destination.getAbsolutePath());
        RootShell.Result result = RootShell.run(45L, command);
        requireSuccess(result, "复制浏览器数据失败");
        syncPrivateFile(destination);
    }

    /** Create the inode as the app so its app-data SELinux/MCS label is retained. */
    private static void createPrivateDestination(File destination) throws IOException {
        try (FileOutputStream ignored = new FileOutputStream(destination, false)) {
            // Creating/truncating the file is the operation we need here.
        }
    }

    /** Persist only the app-private snapshot instead of stalling on a device-wide sync. */
    private static void syncPrivateFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            input.getFD().sync();
        }
    }

    private static void requireSuccess(RootShell.Result result, String message) throws IOException {
        if (!result.isSuccess()) {
            throw new IOException(message + "：" + result.describeFailure());
        }
    }

    private static void requireSafePackageName(String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.]+")) {
            throw new IllegalArgumentException("非法包名");
        }
    }

    private static void requireSafeUserId(int userId) {
        if (userId < 0) {
            throw new IllegalArgumentException("非法 Android 用户 ID");
        }
    }

    private static int currentUserId() {
        return Process.myUid() / PER_USER_RANGE;
    }

    private static String safeName(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._-]+")
                || ".".equals(value)
                || "..".equals(value)) {
            throw new IllegalArgumentException("非法文件标签");
        }
        return value;
    }

    private static String safeTransactionId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("非法 Root 事务 ID");
        }
        return value;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory == null) {
            throw new IOException("工作目录为空");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建工作目录：" + directory.getAbsolutePath());
        }
    }

    private enum MatchResult {
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    private static final class CommandFailure extends IOException {
        private final boolean timedOut;

        private CommandFailure(String message, boolean timedOut) {
            super(message);
            this.timedOut = timedOut;
        }

        private boolean isTimedOut() {
            return timedOut;
        }
    }
}
