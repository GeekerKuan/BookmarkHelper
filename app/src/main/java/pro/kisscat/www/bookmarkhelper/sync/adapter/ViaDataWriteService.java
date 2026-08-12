package pro.kisscat.www.bookmarkhelper.sync.adapter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalBookmark;
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalHistoryVisit;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileGateway;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileMetadata;
import pro.kisscat.www.bookmarkhelper.sync.root.RootTransactionGuard;
import pro.kisscat.www.bookmarkhelper.sync.root.UncertainRootStateException;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/**
 * Writes the current Via SQLite schema through a verified private copy.
 *
 * <p>The live database is never opened by this process. Via is force-stopped,
 * the exact database is snapshotted and backed up, and the validated private
 * copy is installed through the persistent fail-closed Root transaction.</p>
 */
public final class ViaDataWriteService {
    private static final String VIA_PACKAGE = "mark.via";
    private static final int PER_USER_RANGE = 100_000;
    private static final int MAX_RECORDS = 50_000;
    private static final int MAX_URL_LENGTH = 16_384;
    private static final int MAX_TITLE_LENGTH = 4_096;
    private static final int MAX_FOLDER_PATH_LENGTH = 4_096;

    private ViaDataWriteService() {}

    public static WriteResult writeBookmarks(
            Context sourceContext, List<CanonicalBookmark> records) throws IOException {
        if (records == null) throw new IOException("数据管理中的收藏为空");
        if (records.size() > MAX_RECORDS) throw new IOException("收藏数量超过安全上限 " + MAX_RECORDS);
        return writeDatabase(sourceContext, "bookmarks", database -> mergeBookmarks(database, records));
    }

    public static WriteResult writeHistory(
            Context sourceContext, List<CanonicalHistoryVisit> records) throws IOException {
        if (records == null) throw new IOException("数据管理中的历史记录为空");
        if (records.size() > MAX_RECORDS) throw new IOException("历史记录数量超过安全上限 " + MAX_RECORDS);
        return writeDatabase(sourceContext, "history", database -> mergeHistory(database, records));
    }

    private static WriteResult writeDatabase(
            Context sourceContext, String operation, DatabaseMutation mutation) throws IOException {
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard transaction = null;
        boolean prepared = false;
        String databasePath = null;
        File backup = null;
        RootFileMetadata metadata = null;
        try {
            transaction = RootTransactionGuard.acquire(context, "via", operation + "-export");
            transaction.requireNoUnknownState();
            gateway.requireRoot();
            int userId = currentUserId();
            gateway.forceStop(VIA_PACKAGE, userId);
            databasePath = resolveViaDatabase(gateway, userId);
            File original = gateway.snapshotSqliteDatabase(databasePath, "via-export", "via.original");
            rejectPendingSidecars(original);

            File updated = new File(gateway.workingDirectory("via-export"), "via.updated");
            Files.copy(original.toPath(), updated.toPath(), StandardCopyOption.REPLACE_EXISTING);
            WriteResult result = mutatePrivateCopy(updated, mutation);
            if (result.inserted == 0 && result.updated == 0) return result;

            gateway.requireStopped(VIA_PACKAGE, userId);
            gateway.requireFileUnchanged(databasePath, original);
            metadata = gateway.captureMetadata(databasePath);
            backup = gateway.backupFile(databasePath, "via-export");
            transaction.prepareMutation(databasePath, backup, metadata);
            prepared = true;
            try {
                gateway.replaceContentsPreservingMetadata(
                        databasePath,
                        updated,
                        backup,
                        transaction.getTransactionId(),
                        metadata,
                        VIA_PACKAGE,
                        userId);
                try {
                    transaction.completeMutation();
                    prepared = false;
                } catch (IOException journalFailure) {
                    throw new UncertainRootStateException(
                            "Via 数据库已验证，但无法关闭持久 Root 事务日志",
                            false,
                            metadata,
                            journalFailure);
                }
            } catch (UncertainRootStateException uncertain) {
                transaction.markUnknown(
                        uncertain.getMessage(), uncertain.isRebootRequired(),
                        databasePath, backup, metadata);
                prepared = false;
                throw uncertain;
            } catch (IOException knownSafeFailure) {
                transaction.completeMutation();
                prepared = false;
                throw knownSafeFailure;
            }
            LogHelper.v("ViaDataWrite: operation=" + operation
                    + ", inserted=" + result.inserted + ", skipped=" + result.skipped);
            return result;
        } catch (IOException error) {
            if (prepared && transaction != null) {
                try {
                    transaction.markUnknown(
                            "Via 数据事务在写前日志之后异常中断",
                            true,
                            databasePath,
                            backup,
                            metadata);
                } catch (IOException journalFailure) {
                    error.addSuppressed(journalFailure);
                }
            }
            throw error;
        } catch (RuntimeException error) {
            IOException wrapped = new IOException("Via 数据库结构或内容不受支持", error);
            if (prepared && transaction != null) {
                try {
                    transaction.markUnknown(
                            "Via 数据事务在写前日志之后异常中断",
                            true,
                            databasePath,
                            backup,
                            metadata);
                } catch (IOException journalFailure) {
                    wrapped.addSuppressed(journalFailure);
                }
            }
            throw wrapped;
        } finally {
            closeTransaction(transaction);
        }
    }

    private static WriteResult mutatePrivateCopy(File databaseFile, DatabaseMutation mutation)
            throws IOException {
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            WriteResult result = mutation.apply(database);
            requireIntegrity(database);
            return result;
        } finally {
            if (database != null) database.close();
        }
    }

    private static WriteResult mergeBookmarks(
            SQLiteDatabase database, List<CanonicalBookmark> records) throws IOException {
        requireColumns(database, "bookmark_items",
                "_id", "url", "title", "folder_id", "ordering", "created_at", "last_updated_at");
        requireColumns(database, "bookmark_folders",
                "_id", "title", "parent_folder_id", "ordering", "created_at", "last_updated_at");

        Set<String> existingUrls = new HashSet<>();
        Cursor urls = database.rawQuery(
                "SELECT url FROM bookmark_items WHERE url IS NOT NULL AND url<>''", null);
        try {
            while (urls.moveToNext()) existingUrls.add(urls.getString(0).trim());
        } finally {
            urls.close();
        }

        Map<String, String> folders = new HashMap<>();
        Cursor folderRows = database.rawQuery(
                "SELECT _id,title,COALESCE(parent_folder_id,'') FROM bookmark_folders", null);
        try {
            while (folderRows.moveToNext()) {
                folders.put(folderKey(folderRows.getString(2), folderRows.getString(1)), folderRows.getString(0));
            }
        } finally {
            folderRows.close();
        }

        long nowSeconds = System.currentTimeMillis() / 1000L;
        int nextItemOrder = nextOrdering(database, "bookmark_items");
        int nextFolderOrder = nextOrdering(database, "bookmark_folders");
        int inserted = 0;
        int skipped = 0;
        database.beginTransaction();
        try {
            for (CanonicalBookmark record : records) {
                String url = cleanHttpUrl(record.getUrl());
                String title = cleanTitle(record.getTitle(), url);
                String folderPath = normalizeFolderPath(record.getFolderPath());
                if (url == null || title == null || folderPath == null || !existingUrls.add(url)) {
                    skipped++;
                    continue;
                }
                FolderResult folder = ensureFolderPath(
                        database, folders, folderPath, nextFolderOrder, nowSeconds);
                nextFolderOrder = folder.nextOrdering;

                ContentValues values = new ContentValues();
                values.put("_id", UUID.randomUUID().toString());
                values.put("url", url);
                values.put("title", title);
                if (folder.folderId == null) values.putNull("folder_id");
                else values.put("folder_id", folder.folderId);
                values.put("ordering", nextItemOrder++);
                long createdAt = record.getCreatedAtEpochMillis() == null
                        ? nowSeconds : Math.max(0L, record.getCreatedAtEpochMillis() / 1000L);
                values.put("created_at", createdAt);
                values.put("last_updated_at", nowSeconds);
                database.insertOrThrow("bookmark_items", null, values);
                inserted++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return new WriteResult(inserted, skipped, 0);
    }

    private static WriteResult mergeHistory(
            SQLiteDatabase database, List<CanonicalHistoryVisit> records) throws IOException {
        requireColumns(database, "history", "id", "url", "title", "updated_at");
        Set<String> existing = new HashSet<>();
        Cursor rows = database.rawQuery(
                "SELECT url,updated_at FROM history WHERE url IS NOT NULL AND url<>''", null);
        try {
            while (rows.moveToNext()) existing.add(historyKey(rows.getString(0), rows.getLong(1)));
        } finally {
            rows.close();
        }

        int inserted = 0;
        int skipped = 0;
        database.beginTransaction();
        try {
            for (CanonicalHistoryVisit record : records) {
                String url = cleanHttpUrl(record.getUrl());
                long unixSeconds = record.getVisitedAtEpochMillis() / 1000L;
                if (url == null || record.getVisitedAtEpochMillis() < 0L
                        || !existing.add(historyKey(url, unixSeconds))) {
                    skipped++;
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("url", url);
                String title = record.getTitle() == null ? "" : record.getTitle().trim();
                values.put("title", title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH));
                values.put("updated_at", unixSeconds);
                database.insertOrThrow("history", null, values);
                inserted++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return new WriteResult(inserted, skipped, 0);
    }

    private static FolderResult ensureFolderPath(
            SQLiteDatabase database,
            Map<String, String> folders,
            String path,
            int nextOrdering,
            long nowSeconds) {
        if (path.isEmpty()) return new FolderResult(null, nextOrdering);
        String parentId = "";
        for (String name : path.split("/")) {
            String key = folderKey(parentId, name);
            String folderId = folders.get(key);
            if (folderId == null) {
                folderId = UUID.randomUUID().toString();
                ContentValues values = new ContentValues();
                values.put("_id", folderId);
                values.put("title", name);
                if (parentId.isEmpty()) values.putNull("parent_folder_id");
                else values.put("parent_folder_id", parentId);
                values.put("ordering", nextOrdering++);
                values.put("created_at", nowSeconds);
                values.put("last_updated_at", nowSeconds);
                database.insertOrThrow("bookmark_folders", null, values);
                folders.put(key, folderId);
            }
            parentId = folderId;
        }
        return new FolderResult(parentId, nextOrdering);
    }

    private static int nextOrdering(SQLiteDatabase database, String table) {
        Cursor cursor = database.rawQuery(
                "SELECT COALESCE(MAX(ordering),0)+1 FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 1;
        } finally {
            cursor.close();
        }
    }

    private static void requireColumns(SQLiteDatabase database, String table, String... columns)
            throws IOException {
        Set<String> available = new HashSet<>();
        Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (cursor.moveToNext()) available.add(cursor.getString(1));
        } finally {
            cursor.close();
        }
        if (available.isEmpty()) throw new IOException("Via 数据库缺少 " + table + " 表");
        for (String column : columns) {
            if (!available.contains(column)) {
                throw new IOException("Via " + table + " 表缺少 " + column + " 字段");
            }
        }
    }

    private static void requireIntegrity(SQLiteDatabase database) throws IOException {
        Cursor cursor = database.rawQuery("PRAGMA quick_check", null);
        try {
            if (!cursor.moveToFirst() || !"ok".equalsIgnoreCase(cursor.getString(0))) {
                throw new IOException("更新后的 Via 数据库完整性检查失败");
            }
        } finally {
            cursor.close();
        }
    }

    private static void rejectPendingSidecars(File snapshot) throws IOException {
        String path = snapshot.getAbsolutePath();
        File wal = new File(path + "-wal");
        File journal = new File(path + "-journal");
        if ((wal.isFile() && wal.length() > 0L) || (journal.isFile() && journal.length() > 0L)) {
            throw new IOException("Via 数据库仍有未合并事务日志，已拒绝写入；请正常关闭 Via 后重试");
        }
    }

    private static String resolveViaDatabase(RootFileGateway gateway, int userId) throws IOException {
        String user = "/data/user/" + userId + "/" + VIA_PACKAGE;
        String mirror = "/data_mirror/data_ce/null/" + userId + "/" + VIA_PACKAGE;
        String legacyMirror = "/data_mirror/data_ce/" + userId + "/" + VIA_PACKAGE;
        String path = gateway.resolveFirstExistingFile(
                user + "/databases/via",
                "/data/data/" + VIA_PACKAGE + "/databases/via",
                mirror + "/databases/via",
                legacyMirror + "/databases/via");
        if (path == null) throw new IOException("未找到 Via 数据库");
        return path;
    }

    private static String cleanHttpUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_URL_LENGTH) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? value : null;
    }

    private static String cleanTitle(String raw, String fallback) {
        if (fallback == null) return null;
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) value = fallback;
        return value.length() <= MAX_TITLE_LENGTH ? value : null;
    }

    private static String normalizeFolderPath(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String normalized = raw.replace('\\', '/').trim();
        StringBuilder result = new StringBuilder();
        for (String segment : normalized.split("/+")) {
            String clean = segment.trim();
            if (clean.isEmpty() || ".".equals(clean) || "..".equals(clean)) continue;
            if (result.length() > 0) result.append('/');
            result.append(clean);
        }
        return result.length() <= MAX_FOLDER_PATH_LENGTH ? result.toString() : null;
    }

    private static String folderKey(String parentId, String title) {
        return (parentId == null ? "" : parentId) + '\u0000' + title;
    }

    private static String historyKey(String url, long unixSeconds) {
        return url.trim() + '\u0000' + unixSeconds;
    }

    private static int currentUserId() {
        return Process.myUid() / PER_USER_RANGE;
    }

    private static void closeTransaction(RootTransactionGuard transaction) {
        if (transaction == null) return;
        try {
            transaction.close();
        } catch (IOException error) {
            LogHelper.e(error);
        }
    }

    private interface DatabaseMutation {
        WriteResult apply(SQLiteDatabase database) throws IOException;
    }

    private static final class FolderResult {
        final String folderId;
        final int nextOrdering;

        FolderResult(String folderId, int nextOrdering) {
            this.folderId = folderId;
            this.nextOrdering = nextOrdering;
        }
    }

    public static final class WriteResult {
        private final int inserted;
        private final int skipped;
        private final int updated;

        WriteResult(int inserted, int skipped, int updated) {
            this.inserted = inserted;
            this.skipped = skipped;
            this.updated = updated;
        }

        public int getInserted() { return inserted; }
        public int getSkipped() { return skipped; }
        public int getUpdated() { return updated; }
    }
}
