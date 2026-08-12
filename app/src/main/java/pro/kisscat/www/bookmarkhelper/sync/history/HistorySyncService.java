package pro.kisscat.www.bookmarkhelper.sync.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.kisscat.www.bookmarkhelper.sync.root.RootFileGateway;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileMetadata;
import pro.kisscat.www.bookmarkhelper.sync.root.RootTransactionGuard;
import pro.kisscat.www.bookmarkhelper.sync.root.UncertainRootStateException;
import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalHistoryVisit;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/** Safe, independent Via-to-Edge browsing-history importer. */
public final class HistorySyncService {
    private static final String VIA_PACKAGE = "mark.via";
    private static final String EDGE_PACKAGE = "com.microsoft.emmx";
    private static final long CHROMIUM_EPOCH_OFFSET_MICROS = 11_644_473_600_000_000L;
    // PAGE_TRANSITION_LINK | PAGE_TRANSITION_CHAIN_START | PAGE_TRANSITION_CHAIN_END.
    // Chromium's visible-history query requires CHAIN_END; a bare 0 is stored but hidden.
    private static final int VISIBLE_LINK_TRANSITION = 0x30000000;
    private static final int PER_USER_RANGE = 100_000;
    private static final int MAX_HISTORY_ROWS = 50_000;

    private HistorySyncService() {}

    public static Preview preview(Context sourceContext) throws IOException {
        return preview(sourceContext, 0L, Long.MAX_VALUE);
    }

    public static Preview preview(Context sourceContext, long fromUnixSeconds, long toUnixSeconds)
            throws IOException {
        requireValidRange(fromUnixSeconds, toUnixSeconds);
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard guard = null;
        try {
            guard = RootTransactionGuard.acquire(context, "edge", "history-preview");
            guard.requireNoUnknownState();
            gateway.requireRoot();
            int userId = currentUserId();
            List<HistoryEntry> source = readViaHistory(
                    gateway, userId, fromUnixSeconds, toUnixSeconds);
            gateway.forceStop(EDGE_PACKAGE, userId);
            String historyPath = resolveEdgeHistory(gateway, userId);
            File snapshot = gateway.snapshotSqliteDatabase(historyPath, "edge-history", "History");
            EdgeHistoryData existing = readExistingVisits(
                    snapshot, fromUnixSeconds, toUnixSeconds);
            int duplicates = 0;
            long oldest = Long.MAX_VALUE;
            long newest = 0L;
            for (HistoryEntry entry : source) {
                if (existing.visitKeys.contains(
                        visitKey(entry.url, toChromiumMicros(entry.unixSeconds)))) {
                    duplicates++;
                }
                oldest = Math.min(oldest, entry.unixSeconds);
                newest = Math.max(newest, entry.unixSeconds);
            }
            List<CanonicalHistoryVisit> records = new ArrayList<>();
            for (HistoryEntry entry : source) {
                records.add(new CanonicalHistoryVisit(
                        entry.title == null ? "" : entry.title,
                        entry.url,
                        Math.multiplyExact(entry.unixSeconds, 1000L),
                        "link"));
            }
            return new Preview(
                    source.size(), source.size() - duplicates, duplicates,
                    oldest == Long.MAX_VALUE ? 0L : oldest, newest,
                    records, existing.records);
        } finally {
            closeGuard(guard);
        }
    }

    /** Reads Edge's History into the browser-neutral model without mutating the database. */
    public static List<CanonicalHistoryVisit> readEdgeHistory(Context sourceContext)
            throws IOException {
        return readEdgeHistory(sourceContext, 0L, Long.MAX_VALUE);
    }

    /** Reads only the requested Edge history range into the browser-neutral model. */
    public static List<CanonicalHistoryVisit> readEdgeHistory(
            Context sourceContext, long fromUnixSeconds, long toUnixSeconds) throws IOException {
        requireValidRange(fromUnixSeconds, toUnixSeconds);
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard guard = null;
        try {
            guard = RootTransactionGuard.acquire(context, "edge", "history-read");
            guard.requireNoUnknownState();
            gateway.requireRoot();
            int userId = currentUserId();
            gateway.forceStop(EDGE_PACKAGE, userId);
            String historyPath = resolveEdgeHistory(gateway, userId);
            File snapshot = gateway.snapshotSqliteDatabase(
                    historyPath, "edge-history", "History");
            return readExistingVisits(snapshot, fromUnixSeconds, toUnixSeconds).records;
        } finally {
            closeGuard(guard);
        }
    }

    /** Reads Via history without consulting or mutating Edge. */
    public static List<CanonicalHistoryVisit> readViaHistory(
            Context sourceContext, long fromUnixSeconds, long toUnixSeconds) throws IOException {
        requireValidRange(fromUnixSeconds, toUnixSeconds);
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard guard = null;
        try {
            guard = RootTransactionGuard.acquire(context, "via", "history-read");
            guard.requireNoUnknownState();
            gateway.requireRoot();
            List<HistoryEntry> source = readViaHistory(
                    gateway, currentUserId(), fromUnixSeconds, toUnixSeconds);
            List<CanonicalHistoryVisit> records = new ArrayList<>();
            for (HistoryEntry entry : source) {
                records.add(new CanonicalHistoryVisit(
                        entry.title == null ? "" : entry.title,
                        entry.url,
                        Math.multiplyExact(entry.unixSeconds, 1000L),
                        "link"));
            }
            return records;
        } finally {
            closeGuard(guard);
        }
    }

    public static ImportResult importHistory(Context sourceContext) throws IOException {
        return importHistory(sourceContext, 0L, Long.MAX_VALUE);
    }

    public static ImportResult importHistory(
            Context sourceContext, long fromUnixSeconds, long toUnixSeconds) throws IOException {
        return importHistoryInternal(sourceContext, fromUnixSeconds, toUnixSeconds, null);
    }

    /** Imports an edited browser-neutral dataset instead of re-reading Via. */
    public static ImportResult importHistory(
            Context sourceContext, List<CanonicalHistoryVisit> records) throws IOException {
        if (records == null) throw new IOException("中间历史数据为空");
        List<HistoryEntry> source = new ArrayList<>();
        for (CanonicalHistoryVisit record : records) {
            if (!isHttpUrl(record.getUrl())) continue;
            source.add(new HistoryEntry(
                    record.getUrl(), record.getTitle(), record.getVisitedAtEpochMillis() / 1000L));
        }
        return importHistoryInternal(sourceContext, 0L, Long.MAX_VALUE, source);
    }

    private static ImportResult importHistoryInternal(
            Context sourceContext, long fromUnixSeconds, long toUnixSeconds,
            List<HistoryEntry> providedSource) throws IOException {
        requireValidRange(fromUnixSeconds, toUnixSeconds);
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard guard = null;
        boolean prepared = false;
        String historyPath = null;
        File backup = null;
        RootFileMetadata metadata = null;
        try {
            guard = RootTransactionGuard.acquire(context, "edge", "history-import");
            guard.requireNoUnknownState();
            gateway.requireRoot();
            int userId = currentUserId();
            List<HistoryEntry> source = providedSource != null
                    ? providedSource
                    : readViaHistory(gateway, userId, fromUnixSeconds, toUnixSeconds);
            gateway.forceStop(EDGE_PACKAGE, userId);
            historyPath = resolveEdgeHistory(gateway, userId);
            rejectActiveWal(gateway, historyPath);
            File original = gateway.snapshotSqliteDatabase(historyPath, "edge-history", "History");
            File pendingJournal = new File(original.getAbsolutePath() + "-journal");
            if (pendingJournal.isFile() && pendingJournal.length() > 0L) {
                throw new IOException("Edge History 存在未完成的回滚日志，已拒绝写入");
            }
            File updated = new File(gateway.workingDirectory("edge-history"), "History.updated");
            Files.copy(original.toPath(), updated.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ImportResult result = mergeIntoPrivateCopy(updated, source);
            if (result.imported == 0 && result.repaired == 0) return result;

            gateway.requireStopped(EDGE_PACKAGE, userId);
            gateway.requireFileUnchanged(historyPath, original);
            metadata = gateway.captureMetadata(historyPath);
            backup = gateway.backupFile(historyPath, "edge-history");
            guard.prepareMutation(historyPath, backup, metadata);
            prepared = true;
            try {
                gateway.replaceContentsPreservingMetadata(
                        historyPath, updated, backup, guard.getTransactionId(), metadata,
                        EDGE_PACKAGE, userId);
                guard.completeMutation();
                prepared = false;
            } catch (UncertainRootStateException uncertain) {
                guard.markUnknown(
                        uncertain.getMessage(), uncertain.isRebootRequired(),
                        historyPath, backup, metadata);
                throw uncertain;
            } catch (IOException knownSafeFailure) {
                guard.completeMutation();
                prepared = false;
                throw knownSafeFailure;
            }
            LogHelper.v("EdgeHistory: imported=" + result.imported
                    + ", repaired=" + result.repaired
                    + ", skipped=" + result.skipped);
            return result;
        } catch (IOException error) {
            if (prepared && guard != null) {
                guard.markUnknown(
                        "History transaction interrupted", true,
                        historyPath, backup, metadata);
            }
            throw error;
        } finally {
            closeGuard(guard);
        }
    }

    private static List<HistoryEntry> readViaHistory(
            RootFileGateway gateway, int userId, long fromUnixSeconds, long toUnixSeconds)
            throws IOException {
        gateway.forceStop(VIA_PACKAGE, userId);
        String base = "/data/user/" + userId + "/" + VIA_PACKAGE;
        String mirror = "/data_mirror/data_ce/null/" + userId + "/" + VIA_PACKAGE;
        String legacyMirror = "/data_mirror/data_ce/" + userId + "/" + VIA_PACKAGE;
        String path = gateway.resolveFirstExistingFile(
                base + "/databases/via",
                "/data/data/" + VIA_PACKAGE + "/databases/via",
                mirror + "/databases/via",
                legacyMirror + "/databases/via");
        if (path == null) throw new IOException("未找到 Via 历史数据库");
        File snapshot = gateway.snapshotSqliteDatabase(path, "via-history", "via");
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = null;
        try {
            if (!tableExists(database, "history")) {
                throw new IOException("Via 数据库没有 history 表");
            }
            cursor = database.rawQuery(
                    "SELECT url,title,updated_at FROM history "
                            + "WHERE url IS NOT NULL AND url <> '' "
                            + "AND updated_at >= ? AND updated_at <= ? "
                            + "ORDER BY updated_at ASC LIMIT " + (MAX_HISTORY_ROWS + 1),
                    new String[]{Long.toString(fromUnixSeconds), Long.toString(toUnixSeconds)});
            if (cursor.getCount() > MAX_HISTORY_ROWS) {
                throw new IOException("Via 历史记录超过安全上限 " + MAX_HISTORY_ROWS);
            }
            List<HistoryEntry> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                String url = cursor.getString(0);
                if (!isHttpUrl(url)) continue;
                result.add(new HistoryEntry(url, cursor.getString(1), cursor.getLong(2)));
            }
            return result;
        } finally {
            if (cursor != null) cursor.close();
            database.close();
        }
    }

    private static EdgeHistoryData readExistingVisits(
            File history, long fromUnixSeconds, long toUnixSeconds) throws IOException {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                history.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = null;
        try {
            requireHistorySchema(database);
            long from = toChromiumMicros(fromUnixSeconds);
            long to = toUnixSeconds == Long.MAX_VALUE
                    ? Long.MAX_VALUE : toChromiumMicros(toUnixSeconds);
            cursor = database.rawQuery(
                    "SELECT u.title,u.url,v.visit_time,v.transition "
                            + "FROM visits v JOIN urls u ON u.id=v.url "
                            + "WHERE v.visit_time>=? AND v.visit_time<=? "
                            + "ORDER BY v.visit_time ASC LIMIT " + (MAX_HISTORY_ROWS + 1),
                    new String[]{Long.toString(from), Long.toString(to)});
            if (cursor.getCount() > MAX_HISTORY_ROWS) {
                throw new IOException("Edge 历史记录超过安全上限 " + MAX_HISTORY_ROWS);
            }
            Set<String> keys = new HashSet<>();
            List<CanonicalHistoryVisit> records = new ArrayList<>();
            while (cursor.moveToNext()) {
                String url = cursor.getString(1);
                if (!isHttpUrl(url)) continue;
                long chromiumMicros = cursor.getLong(2);
                keys.add(visitKey(url, chromiumMicros));
                records.add(new CanonicalHistoryVisit(
                        cursor.isNull(0) ? "" : cursor.getString(0),
                        url,
                        fromChromiumMicros(chromiumMicros),
                        Integer.toString(cursor.getInt(3))));
            }
            return new EdgeHistoryData(keys, records);
        } finally {
            if (cursor != null) cursor.close();
            database.close();
        }
    }

    private static ImportResult mergeIntoPrivateCopy(File history, List<HistoryEntry> source)
            throws IOException {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                history.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
        int imported = 0;
        int skipped = 0;
        int repaired = 0;
        try {
            requireHistorySchema(database);
            Map<String, Long> urlIds = readUrlIds(database);
            Map<String, Long> visitIds = readVisitIds(database, source);
            database.beginTransaction();
            try {
                for (HistoryEntry entry : source) {
                    long visitTime = toChromiumMicros(entry.unixSeconds);
                    Long knownUrlId = urlIds.get(entry.url);
                    long urlId = knownUrlId == null ? -1L : knownUrlId;
                    Long existingVisitId = visitIds.get(visitKey(entry.url, visitTime));
                    if (existingVisitId != null) {
                        if (repairExistingVisit(database, existingVisitId)) repaired++;
                        else skipped++;
                        continue;
                    }
                    if (urlId <= 0) {
                        ContentValues url = new ContentValues();
                        url.put("url", entry.url);
                        url.put("title", entry.title == null ? "" : entry.title);
                        url.put("visit_count", 1);
                        url.put("typed_count", 0);
                        url.put("last_visit_time", visitTime);
                        url.put("hidden", 0);
                        urlId = database.insertOrThrow("urls", null, url);
                        urlIds.put(entry.url, urlId);
                    } else {
                        database.execSQL(
                                "UPDATE urls SET visit_count=visit_count+1, "
                                        + "last_visit_time=MAX(last_visit_time,?) WHERE id=?",
                                new Object[]{visitTime, urlId});
                    }
                    ContentValues visit = new ContentValues();
                    visit.put("url", urlId);
                    visit.put("visit_time", visitTime);
                    visit.put("from_visit", 0);
                    visit.put("transition", VISIBLE_LINK_TRANSITION);
                    visit.put("segment_id", 0);
                    visit.put("visit_duration", 0);
                    visit.put("incremented_omnibox_typed_score", false);
                    visit.put("opener_visit", 0);
                    visit.put("originator_visit_id", 0);
                    visit.put("originator_from_visit", 0);
                    visit.put("originator_opener_visit", 0);
                    visit.put("is_known_to_sync", true);
                    visit.put("consider_for_ntp_most_visited", true);
                    visit.put("visited_link_id", 0);
                    long visitId = database.insertOrThrow("visits", null, visit);
                    // Chromium intentionally stores no visit_source row for SOURCE_BROWSED.
                    insertContextAnnotationsIfSupported(database, visitId);
                    visitIds.put(visitKey(entry.url, visitTime), visitId);
                    imported++;
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
            Cursor check = database.rawQuery("PRAGMA quick_check", null);
            try {
                if (!check.moveToFirst() || !"ok".equalsIgnoreCase(check.getString(0))) {
                    throw new IOException("更新后的 Edge History 完整性检查失败");
                }
            } finally {
                check.close();
            }
            return new ImportResult(imported, skipped, repaired);
        } finally {
            database.close();
        }
    }

    private static Map<String, Long> readUrlIds(SQLiteDatabase database) {
        Map<String, Long> result = new HashMap<>();
        Cursor cursor = database.rawQuery("SELECT url,id FROM urls", null);
        try {
            while (cursor.moveToNext()) result.put(cursor.getString(0), cursor.getLong(1));
            return result;
        } finally { cursor.close(); }
    }

    private static Map<String, Long> readVisitIds(
            SQLiteDatabase database, List<HistoryEntry> source) throws IOException {
        Map<String, Long> result = new HashMap<>();
        if (source.isEmpty()) return result;
        long oldest = Long.MAX_VALUE;
        long newest = 0L;
        for (HistoryEntry entry : source) {
            long visitTime = toChromiumMicros(entry.unixSeconds);
            oldest = Math.min(oldest, visitTime);
            newest = Math.max(newest, visitTime);
        }
        Cursor cursor = database.rawQuery(
                "SELECT u.url,v.visit_time,v.id FROM visits v JOIN urls u ON u.id=v.url "
                        + "WHERE v.visit_time>=? AND v.visit_time<=?",
                new String[]{Long.toString(oldest), Long.toString(newest)});
        try {
            while (cursor.moveToNext()) {
                result.put(visitKey(cursor.getString(0), cursor.getLong(1)), cursor.getLong(2));
            }
            return result;
        } finally { cursor.close(); }
    }

    private static boolean repairExistingVisit(SQLiteDatabase database, long visitId) {
        Cursor cursor = database.rawQuery(
                "SELECT transition,is_known_to_sync,consider_for_ntp_most_visited "
                        + "FROM visits WHERE id=?",
                new String[]{Long.toString(visitId)});
        int transition;
        int knownToSync;
        int considerForNtp;
        try {
            if (!cursor.moveToFirst()) return false;
            transition = cursor.getInt(0);
            knownToSync = cursor.getInt(1);
            considerForNtp = cursor.getInt(2);
        } finally {
            cursor.close();
        }
        Cursor source = database.rawQuery(
                "SELECT source FROM visit_source WHERE id=? LIMIT 1",
                new String[]{Long.toString(visitId)});
        boolean hasSource;
        int sourceType;
        try {
            hasSource = source.moveToFirst();
            sourceType = hasSource ? source.getInt(0) : -1;
        } finally { source.close(); }
        // Only repair the exact signature written by BookmarkHelper alpha05/06.
        // A legitimate Edge synced visit also has source=0, so never infer from source alone.
        if (!isLegacyInvisibleVisit(
                transition, knownToSync, considerForNtp, hasSource, sourceType)) return false;

        ContentValues values = new ContentValues();
        values.put("from_visit", 0);
        values.put("transition", VISIBLE_LINK_TRANSITION);
        values.put("segment_id", 0);
        values.put("opener_visit", 0);
        values.put("is_known_to_sync", true);
        values.put("consider_for_ntp_most_visited", true);
        database.update("visits", values, "id=?", new String[]{Long.toString(visitId)});
        database.delete("visit_source", "id=?", new String[]{Long.toString(visitId)});
        insertContextAnnotationsIfSupported(database, visitId);
        return true;
    }

    private static void insertContextAnnotationsIfSupported(
            SQLiteDatabase database, long visitId) {
        if (!tableExists(database, "context_annotations")) return;
        ContentValues context = new ContentValues();
        context.put("visit_id", visitId);
        context.put("context_annotation_flags", 0);
        context.put("browser_type", 0);
        context.put("window_id", -1);
        context.put("tab_id", -1);
        context.put("task_id", -1);
        context.put("root_task_id", -1);
        context.put("parent_task_id", -1);
        context.put("response_code", 0);
        database.insertWithOnConflict(
                "context_annotations", null, context, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void requireHistorySchema(SQLiteDatabase database) throws IOException {
        if (!tableExists(database, "urls")
                || !tableExists(database, "visits")
                || !tableExists(database, "visit_source")) {
            throw new IOException("Edge History 数据库结构不受支持");
        }
    }

    private static boolean tableExists(SQLiteDatabase database, String table) {
        Cursor cursor = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table});
        try { return cursor.moveToFirst(); }
        finally { cursor.close(); }
    }

    private static String resolveEdgeHistory(RootFileGateway gateway, int userId) throws IOException {
        String user = "/data/user/" + userId + "/" + EDGE_PACKAGE;
        String mirror = "/data_mirror/data_ce/null/" + userId + "/" + EDGE_PACKAGE;
        String legacyMirror = "/data_mirror/data_ce/" + userId + "/" + EDGE_PACKAGE;
        String path = gateway.resolveFirstExistingFile(
                user + "/app_chrome/Default/History",
                "/data/data/" + EDGE_PACKAGE + "/app_chrome/Default/History",
                mirror + "/app_chrome/Default/History",
                legacyMirror + "/app_chrome/Default/History");
        if (path == null) throw new IOException("未找到 Edge Default Profile 的 History");
        return path;
    }

    private static void rejectActiveWal(RootFileGateway gateway, String path) throws IOException {
        if (gateway.resolveFirstExistingFile(path + "-wal", path + "-shm") != null) {
            throw new IOException("Edge History 正在使用 WAL；为保护数据，本版本拒绝写入");
        }
    }

    private static long toChromiumMicros(long unixSeconds) throws IOException {
        if (unixSeconds < 0 || unixSeconds > (Long.MAX_VALUE - CHROMIUM_EPOCH_OFFSET_MICROS) / 1_000_000L) {
            throw new IOException("Via 历史时间戳超出支持范围");
        }
        return CHROMIUM_EPOCH_OFFSET_MICROS + unixSeconds * 1_000_000L;
    }

    private static long fromChromiumMicros(long chromiumMicros) throws IOException {
        if (chromiumMicros < CHROMIUM_EPOCH_OFFSET_MICROS) {
            throw new IOException("Edge 历史时间戳早于 Unix epoch");
        }
        return (chromiumMicros - CHROMIUM_EPOCH_OFFSET_MICROS) / 1_000L;
    }

    private static void requireValidRange(long fromUnixSeconds, long toUnixSeconds)
            throws IOException {
        if (fromUnixSeconds < 0L || toUnixSeconds < fromUnixSeconds) {
            throw new IOException("Invalid history time range");
        }
    }

    static long toChromiumMicrosForTest(long unixSeconds) throws IOException {
        return toChromiumMicros(unixSeconds);
    }

    static long fromChromiumMicrosForTest(long chromiumMicros) throws IOException {
        return fromChromiumMicros(chromiumMicros);
    }

    static int visibleLinkTransitionForTest() { return VISIBLE_LINK_TRANSITION; }

    static boolean isLegacyInvisibleVisitForTest(
            int transition, int knownToSync, int considerForNtp,
            boolean hasSource, int sourceType) {
        return isLegacyInvisibleVisit(
                transition, knownToSync, considerForNtp, hasSource, sourceType);
    }

    private static boolean isLegacyInvisibleVisit(
            int transition, int knownToSync, int considerForNtp,
            boolean hasSource, int sourceType) {
        return transition == 0 && knownToSync == 0 && considerForNtp == 0
                && hasSource && sourceType == 0;
    }

    private static String visitKey(String url, long visitTime) {
        return url + '\u0000' + visitTime;
    }

    private static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static int currentUserId() { return Process.myUid() / PER_USER_RANGE; }

    private static void closeGuard(RootTransactionGuard guard) {
        if (guard == null) return;
        try { guard.close(); } catch (IOException error) { LogHelper.e(error); }
    }

    private static final class HistoryEntry {
        final String url;
        final String title;
        final long unixSeconds;
        HistoryEntry(String url, String title, long unixSeconds) {
            this.url = url;
            this.title = title;
            this.unixSeconds = unixSeconds;
        }
    }

    private static final class EdgeHistoryData {
        final Set<String> visitKeys;
        final List<CanonicalHistoryVisit> records;
        EdgeHistoryData(Set<String> visitKeys, List<CanonicalHistoryVisit> records) {
            this.visitKeys = visitKeys;
            this.records = records;
        }
    }

    public static final class Preview {
        private final int total;
        private final int newCount;
        private final int duplicateCount;
        private final long oldestUnixSeconds;
        private final long newestUnixSeconds;
        private final List<CanonicalHistoryVisit> records;
        private final List<CanonicalHistoryVisit> edgeRecords;
        Preview(int total, int newCount, int duplicateCount, long oldest, long newest,
                List<CanonicalHistoryVisit> records,
                List<CanonicalHistoryVisit> edgeRecords) {
            this.total = total;
            this.newCount = newCount;
            this.duplicateCount = duplicateCount;
            this.oldestUnixSeconds = oldest;
            this.newestUnixSeconds = newest;
            this.records = records;
            this.edgeRecords = edgeRecords;
        }
        public int getTotal() { return total; }
        public int getNewCount() { return newCount; }
        public int getDuplicateCount() { return duplicateCount; }
        public long getOldestUnixSeconds() { return oldestUnixSeconds; }
        public long getNewestUnixSeconds() { return newestUnixSeconds; }
        public List<CanonicalHistoryVisit> getRecords() { return records; }
        public List<CanonicalHistoryVisit> getEdgeRecords() { return edgeRecords; }
    }

    public static final class ImportResult {
        private final int imported;
        private final int skipped;
        private final int repaired;
        ImportResult(int imported, int skipped, int repaired) {
            this.imported = imported;
            this.skipped = skipped;
            this.repaired = repaired;
        }
        public int getImported() { return imported; }
        public int getSkipped() { return skipped; }
        public int getRepaired() { return repaired; }
    }
}
