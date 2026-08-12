package pro.kisscat.www.bookmarkhelper.converter.support.impl.edge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import pro.kisscat.www.bookmarkhelper.R;
import pro.kisscat.www.bookmarkhelper.converter.support.impl.chrome.ChromeBrowserAble;
import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;
import pro.kisscat.www.bookmarkhelper.exception.ConverterException;
import pro.kisscat.www.bookmarkhelper.sync.chromium.ChromiumBookmarksFile;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileGateway;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileMetadata;
import pro.kisscat.www.bookmarkhelper.sync.root.RootTransactionGuard;
import pro.kisscat.www.bookmarkhelper.sync.root.UncertainRootStateException;
import pro.kisscat.www.bookmarkhelper.util.context.ContextUtil;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/** Microsoft Edge stable channel target for Root-based bookmark import. */
public final class EdgeBrowser extends ChromeBrowserAble {
    public static final String PACKAGE_NAME = "com.microsoft.emmx";
    private static final String TAG = "Edge";
    private static final String TRANSACTION_SCOPE = "edge";
    // Mirrors Android's hidden UserHandle.PER_USER_RANGE.
    private static final int PER_USER_RANGE = 100000;

    private List<Bookmark> bookmarks;

    public EdgeBrowser() {
        super.TAG = TAG;
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }

    @Override
    public int readBookmarkSum() {
        if (bookmarks == null) {
            readBookmark();
        }
        return bookmarks.size();
    }

    @Override
    public void fillDefaultIcon(Context context) {
        try {
            this.setIcon(context.getPackageManager().getApplicationIcon(PACKAGE_NAME));
        } catch (PackageManager.NameNotFoundException ignored) {
            this.setIcon(ContextCompat.getDrawable(context, R.mipmap.ic_launcher));
        }
    }

    @Override
    public void fillDefaultAppName(Context context) {
        this.setName(context.getString(R.string.browser_name_show_edge));
    }

    @Override
    public List<Bookmark> readBookmark() {
        Context context = ContextUtil.getApplicationContext();
        RootTransactionGuard transaction = null;
        boolean edgeStopped = false;
        boolean safeToLaunch = false;
        try {
            transaction = RootTransactionGuard.acquire(context, TRANSACTION_SCOPE, "read");
            transaction.requireNoUnknownState();
            RootFileGateway gateway = new RootFileGateway(context);
            gateway.requireRoot();
            int userId = currentUserId();
            LogHelper.v(TAG + ":正在停止 Edge 进程");
            gateway.forceStop(PACKAGE_NAME, userId);
            LogHelper.v(TAG + ":Edge 进程已停止，正在定位 Bookmarks");
            edgeStopped = true;
            safeToLaunch = true;
            String path = resolveBookmarksPath(gateway);
            File snapshot = gateway.snapshotFile(path, "edge", "Bookmarks");
            bookmarks = new LinkedList<>();
            fetchValidBookmarks(bookmarks, fetchBookmarks(snapshot));
            return bookmarks;
        } catch (Exception error) {
            LogHelper.e(error);
            throw new ConverterException("读取 Edge 收藏夹失败：" + safeMessage(error));
        } finally {
            closeTransaction(transaction);
        }
    }

    @Override
    public int appendBookmark(List<Bookmark> incoming) {
        Context context = ContextUtil.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard transaction = null;
        boolean edgeStopped = false;
        boolean safeToLaunch = false;
        boolean mutationPrepared = false;
        String edgePath = null;
        File recoveryBackup = null;
        RootFileMetadata expectedMetadata = null;
        try {
            transaction = RootTransactionGuard.acquire(context, TRANSACTION_SCOPE, "import");
            transaction.requireNoUnknownState();
            gateway.requireRoot();
            int userId = currentUserId();
            LogHelper.v(TAG + ":正在停止 Edge 进程以执行导入");
            gateway.forceStop(PACKAGE_NAME, userId);
            LogHelper.v(TAG + ":Edge 进程已停止，正在定位 Bookmarks");
            edgeStopped = true;
            safeToLaunch = true;
            edgePath = resolveBookmarksPath(gateway);
            LogHelper.v(TAG + ":Bookmarks 路径已定位，正在创建只读快照");
            File snapshot = gateway.snapshotFile(edgePath, "edge", "Bookmarks");
            File updated = new File(gateway.workingDirectory("edge"), "Bookmarks.updated");
            ChromiumBookmarksFile.MergeResult merge =
                    ChromiumBookmarksFile.merge(snapshot, updated, incoming);
            if (merge.getImported() == 0) {
                return 0;
            }

            gateway.requireStopped(PACKAGE_NAME, userId);
            gateway.requireFileUnchanged(edgePath, snapshot);
            expectedMetadata = gateway.captureMetadata(edgePath);
            recoveryBackup = gateway.backupFile(edgePath, "edge");
            transaction.prepareMutation(edgePath, recoveryBackup, expectedMetadata);
            mutationPrepared = true;
            try {
                gateway.replaceContentsPreservingMetadata(
                        edgePath,
                        updated,
                        recoveryBackup,
                        transaction.getTransactionId(),
                        expectedMetadata,
                        PACKAGE_NAME,
                        userId);
                completePreparedMutation(transaction, expectedMetadata);
                mutationPrepared = false;
            } catch (UncertainRootStateException uncertain) {
                safeToLaunch = false;
                persistUnknown(transaction, uncertain, edgePath, recoveryBackup, expectedMetadata);
                throw uncertain;
            } catch (IOException knownSafeFailure) {
                try {
                    transaction.completeMutation();
                    mutationPrepared = false;
                } catch (IOException journalFailure) {
                    UncertainRootStateException uncertain = new UncertainRootStateException(
                            "Edge 已恢复到已知状态，但无法关闭持久事务日志",
                            false,
                            expectedMetadata,
                            journalFailure);
                    uncertain.addSuppressed(knownSafeFailure);
                    safeToLaunch = false;
                    persistUnknown(transaction, uncertain, edgePath, recoveryBackup, expectedMetadata);
                    throw uncertain;
                }
                throw knownSafeFailure;
            }

            bookmarks = null;
            LogHelper.v(TAG + ": imported=" + merge.getImported()
                    + ", skipped=" + merge.getSkipped()
                    + ", totalUrls=" + merge.getTotalUniqueUrls()
                    + ", backup=" + recoveryBackup.getAbsolutePath());
            return merge.getImported();
        } catch (Exception error) {
            if (mutationPrepared) {
                safeToLaunch = false;
                RootFileMetadata metadata = metadataFrom(error, expectedMetadata);
                UncertainRootStateException uncertain = error instanceof UncertainRootStateException
                        ? (UncertainRootStateException) error
                        : new UncertainRootStateException(
                                "Edge Root 事务在写前日志之后异常中断",
                                true,
                                metadata,
                                error);
                persistUnknown(transaction, uncertain, edgePath, recoveryBackup, metadata);
                error = uncertain;
            } else if (error instanceof UncertainRootStateException) {
                safeToLaunch = false;
            }
            LogHelper.e(error);
            throw new ConverterException("写入 Edge 收藏夹失败：" + safeMessage(error));
        } finally {
            closeTransaction(transaction);
        }
    }

    public static String restoreLatestBackup() {
        Context context = ContextUtil.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard transaction = null;
        RootTransactionGuard.UnknownState previousUnknown = null;
        boolean edgeStopped = false;
        boolean safeToLaunch = false;
        boolean mutationPrepared = false;
        String edgePath = null;
        File recoverySource = null;
        File rollbackBackup = null;
        File journalBackup = null;
        RootFileMetadata expectedMetadata = null;
        try {
            transaction = RootTransactionGuard.acquire(context, TRANSACTION_SCOPE, "restore");
            previousUnknown = transaction.readUnknownState();
            if (previousUnknown != null) {
                transaction.requireRecoveryAllowed(previousUnknown);
                expectedMetadata = previousUnknown.getExpectedMetadata();
                if (expectedMetadata == null) {
                    throw new IOException("UNKNOWN 事务缺少原始文件元数据，拒绝自动恢复");
                }
                recoverySource = verifiedRecordedBackup(context, previousUnknown);
            } else {
                recoverySource = gateway.latestBackup("edge");
            }
            if (recoverySource == null) {
                throw new IOException("还没有可恢复的 Edge 备份");
            }
            ChromiumBookmarksFile.validate(recoverySource);

            gateway.requireRoot();
            int userId = currentUserId();
            gateway.forceStop(PACKAGE_NAME, userId);
            edgeStopped = true;
            safeToLaunch = previousUnknown == null;
            edgePath = resolveBookmarksPath(gateway);
            if (previousUnknown != null
                    && !previousUnknown.getTargetPath().isEmpty()
                    && !edgePath.equals(previousUnknown.getTargetPath())) {
                throw new IOException("Edge Profile 路径与 UNKNOWN 事务记录不一致，拒绝自动恢复");
            }
            File current = gateway.snapshotFile(edgePath, "edge", "Bookmarks.before-restore");
            gateway.requireStopped(PACKAGE_NAME, userId);
            gateway.requireFileUnchanged(edgePath, current);
            if (expectedMetadata == null) {
                expectedMetadata = gateway.captureMetadata(edgePath);
            }
            rollbackBackup = gateway.backupFile(edgePath, "edge_restore_undo");

            if (previousUnknown == null) {
                journalBackup = rollbackBackup;
                transaction.prepareMutation(edgePath, journalBackup, expectedMetadata);
            } else {
                journalBackup = recoverySource;
                transaction.prepareRecoveryAttempt(previousUnknown);
            }
            mutationPrepared = true;
            try {
                gateway.replaceContentsPreservingMetadata(
                        edgePath,
                        recoverySource,
                        rollbackBackup,
                        transaction.getTransactionId(),
                        expectedMetadata,
                        PACKAGE_NAME,
                        userId);
                completePreparedMutation(transaction, expectedMetadata);
                mutationPrepared = false;
                safeToLaunch = true;
            } catch (UncertainRootStateException uncertain) {
                safeToLaunch = false;
                persistUnknown(transaction, uncertain, edgePath, journalBackup, expectedMetadata);
                throw uncertain;
            } catch (IOException knownSafeFailure) {
                if (previousUnknown != null) {
                    safeToLaunch = false;
                    UncertainRootStateException uncertain = new UncertainRootStateException(
                            "手动恢复失败；已回到恢复前的 UNKNOWN 状态",
                            false,
                            expectedMetadata,
                            knownSafeFailure);
                    persistUnknown(transaction, uncertain, edgePath, journalBackup, expectedMetadata);
                    throw uncertain;
                }
                try {
                    transaction.completeMutation();
                    mutationPrepared = false;
                } catch (IOException journalFailure) {
                    UncertainRootStateException uncertain = new UncertainRootStateException(
                            "Edge 已回滚，但无法关闭持久事务日志",
                            false,
                            expectedMetadata,
                            journalFailure);
                    uncertain.addSuppressed(knownSafeFailure);
                    safeToLaunch = false;
                    persistUnknown(transaction, uncertain, edgePath, journalBackup, expectedMetadata);
                    throw uncertain;
                }
                throw knownSafeFailure;
            }

            LogHelper.v(TAG + ": restored backup=" + recoverySource.getName()
                    + ", undo=" + rollbackBackup.getAbsolutePath());
            return recoverySource.getName();
        } catch (Exception error) {
            if (mutationPrepared) {
                safeToLaunch = false;
                RootFileMetadata metadata = metadataFrom(error, expectedMetadata);
                UncertainRootStateException uncertain = error instanceof UncertainRootStateException
                        ? (UncertainRootStateException) error
                        : new UncertainRootStateException(
                                "Edge 恢复事务在写前日志之后异常中断",
                                true,
                                metadata,
                                error);
                persistUnknown(transaction, uncertain, edgePath, journalBackup, metadata);
                error = uncertain;
            } else if (previousUnknown != null || error instanceof UncertainRootStateException) {
                safeToLaunch = false;
            }
            LogHelper.e(error);
            throw new ConverterException("恢复 Edge 备份失败：" + safeMessage(error));
        } finally {
            closeTransaction(transaction);
        }
    }

    private static void completePreparedMutation(
            RootTransactionGuard transaction,
            RootFileMetadata expectedMetadata) throws UncertainRootStateException {
        try {
            transaction.completeMutation();
        } catch (IOException error) {
            throw new UncertainRootStateException(
                    "Edge 文件已验证，但无法关闭持久 Root 事务日志",
                    false,
                    expectedMetadata,
                    error);
        }
    }

    private static void persistUnknown(
            RootTransactionGuard transaction,
            UncertainRootStateException uncertain,
            String targetPath,
            File recoveryBackup,
            RootFileMetadata fallbackMetadata) {
        if (transaction == null) {
            return;
        }
        RootFileMetadata metadata = metadataFrom(uncertain, fallbackMetadata);
        try {
            transaction.markUnknown(
                    uncertain.getMessage(),
                    uncertain.isRebootRequired(),
                    targetPath,
                    recoveryBackup,
                    metadata);
        } catch (IOException journalFailure) {
            // prepareMutation() was persisted before the root write. Preserve that
            // PREPARED record if this more detailed update cannot be written.
            uncertain.addSuppressed(journalFailure);
        }
    }

    private static RootFileMetadata metadataFrom(
            Exception error,
            RootFileMetadata fallback) {
        if (error instanceof UncertainRootStateException) {
            RootFileMetadata metadata =
                    ((UncertainRootStateException) error).getExpectedMetadata();
            if (metadata != null) {
                return metadata;
            }
        }
        return fallback;
    }

    private static File verifiedRecordedBackup(
            Context context,
            RootTransactionGuard.UnknownState state) throws IOException {
        if (state.getBackupPath().isEmpty()) {
            throw new IOException("UNKNOWN 事务没有记录可恢复备份");
        }
        File backupRoot = new File(context.getFilesDir(), "browser_backups").getCanonicalFile();
        File candidate = new File(state.getBackupPath()).getCanonicalFile();
        String rootPrefix = backupRoot.getPath() + File.separator;
        if (!candidate.getPath().startsWith(rootPrefix)
                || !candidate.isFile()
                || candidate.length() == 0L) {
            throw new IOException("UNKNOWN 事务记录的备份路径无效");
        }
        return candidate;
    }

    private static void closeTransaction(RootTransactionGuard transaction) {
        if (transaction == null) {
            return;
        }
        try {
            transaction.close();
        } catch (IOException error) {
            LogHelper.e(error);
        }
    }

    private static String resolveBookmarksPath(RootFileGateway gateway) throws IOException {
        int userId = currentUserId();
        String userBase = "/data/user/" + userId + "/" + PACKAGE_NAME;
        String compatibilityBase = "/data/data/" + PACKAGE_NAME;
        String mirroredBase = "/data_mirror/data_ce/null/" + userId + "/" + PACKAGE_NAME;
        String legacyMirroredBase = "/data_mirror/data_ce/" + userId + "/" + PACKAGE_NAME;
        String path = userId == 0
                ? gateway.resolveFirstExistingFile(
                        userBase + "/app_chrome/Default/Bookmarks",
                        compatibilityBase + "/app_chrome/Default/Bookmarks",
                        userBase + "/app_edge/Default/Bookmarks",
                        userBase + "/app_chromium/Default/Bookmarks",
                        mirroredBase + "/app_chrome/Default/Bookmarks",
                        mirroredBase + "/app_edge/Default/Bookmarks",
                        mirroredBase + "/app_chromium/Default/Bookmarks",
                        legacyMirroredBase + "/app_chrome/Default/Bookmarks")
                : gateway.resolveFirstExistingFile(
                        userBase + "/app_chrome/Default/Bookmarks",
                        userBase + "/app_edge/Default/Bookmarks",
                        userBase + "/app_chromium/Default/Bookmarks",
                        mirroredBase + "/app_chrome/Default/Bookmarks",
                        mirroredBase + "/app_edge/Default/Bookmarks",
                        mirroredBase + "/app_chromium/Default/Bookmarks",
                        legacyMirroredBase + "/app_chrome/Default/Bookmarks");
        if (path == null || path.isEmpty()) {
            String unsupported = userId == 0
                    ? gateway.resolveFirstExistingFile(
                            userBase + "/app_chrome/Default/EncryptedBookmarks2",
                            userBase + "/app_chrome/Default/EncryptedBookmarks",
                            compatibilityBase + "/app_chrome/Default/EncryptedBookmarks2",
                            compatibilityBase + "/app_chrome/Default/EncryptedBookmarks",
                            userBase + "/app_chrome/Default/AccountBookmarks",
                            compatibilityBase + "/app_chrome/Default/AccountBookmarks",
                            mirroredBase + "/app_chrome/Default/EncryptedBookmarks2",
                            mirroredBase + "/app_chrome/Default/EncryptedBookmarks",
                            mirroredBase + "/app_chrome/Default/AccountBookmarks",
                            legacyMirroredBase + "/app_chrome/Default/EncryptedBookmarks2",
                            legacyMirroredBase + "/app_chrome/Default/EncryptedBookmarks",
                            legacyMirroredBase + "/app_chrome/Default/AccountBookmarks")
                    : gateway.resolveFirstExistingFile(
                            userBase + "/app_chrome/Default/EncryptedBookmarks2",
                            userBase + "/app_chrome/Default/EncryptedBookmarks",
                            userBase + "/app_chrome/Default/AccountBookmarks",
                            mirroredBase + "/app_chrome/Default/EncryptedBookmarks2",
                            mirroredBase + "/app_chrome/Default/EncryptedBookmarks",
                            mirroredBase + "/app_chrome/Default/AccountBookmarks",
                            legacyMirroredBase + "/app_chrome/Default/EncryptedBookmarks2",
                            legacyMirroredBase + "/app_chrome/Default/EncryptedBookmarks",
                            legacyMirroredBase + "/app_chrome/Default/AccountBookmarks");
            if (unsupported != null) {
                throw new IOException("当前 Edge 使用账号书签或加密书签格式，首个测试版会停止写入以保护数据");
            }
            throw new IOException("未找到 Default Profile 的明文 Bookmarks；请确认 edge://version 的 Profile Path");
        }
        rejectEncryptedSibling(gateway, path);
        return path;
    }

    private static void rejectEncryptedSibling(RootFileGateway gateway, String bookmarksPath)
            throws IOException {
        File profile = new File(bookmarksPath).getParentFile();
        if (profile == null) {
            throw new IOException("Edge Profile 路径无效");
        }
        String encrypted = gateway.resolveFirstExistingFile(
                new File(profile, "EncryptedBookmarks2").getAbsolutePath(),
                new File(profile, "EncryptedBookmarks").getAbsolutePath());
        if (encrypted != null) {
            throw new IOException("检测到 Edge 加密书签文件，首个测试版会停止写入以保护数据");
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static int currentUserId() {
        return Process.myUid() / PER_USER_RANGE;
    }
}
