package pro.kisscat.www.bookmarkhelper.sync.tabs;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.kisscat.www.bookmarkhelper.sync.model.CanonicalOpenTab;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileGateway;
import pro.kisscat.www.bookmarkhelper.sync.root.RootTransactionGuard;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/** Read-only adapters for the browser sessions that are actually open now. */
public final class OpenTabsSyncService {
    private static final String VIA_PACKAGE = "mark.via";
    private static final String EDGE_PACKAGE = "com.microsoft.emmx";
    private static final int PER_USER_RANGE = 100_000;
    private static final int MAX_TABS = 10_000;

    private OpenTabsSyncService() {}

    public static List<CanonicalOpenTab> readViaTabs(Context sourceContext) throws IOException {
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard guard = null;
        String stage = "lock";
        try {
            guard = RootTransactionGuard.acquire(context, "via", "tabs-read");
            guard.requireNoUnknownState();
            stage = "root";
            gateway.requireRoot();
            int userId = currentUserId();
            stage = "stop";
            gateway.forceStop(VIA_PACKAGE, userId);
            stage = "resolve";
            String databasePath = resolveViaDatabase(gateway, userId);
            if (databasePath == null) throw new IOException("未找到 Via 标签页数据库");
            stage = "snapshot";
            File snapshot = gateway.snapshotSqliteDatabase(databasePath, "via-tabs", "via");
            stage = "parse";
            List<CanonicalOpenTab> result = readViaSnapshot(snapshot);
            LogHelper.v("OpenTabsMeta", "browser=VIA stage=complete count=" + result.size());
            return result;
        } catch (IOException error) {
            LogHelper.e("OpenTabsMeta", "browser=VIA stage=" + stage
                    + " type=" + error.getClass().getSimpleName());
            throw error;
        } finally {
            closeGuard(guard);
        }
    }

    public static List<CanonicalOpenTab> readEdgeTabs(Context sourceContext) throws IOException {
        Context context = sourceContext.getApplicationContext();
        RootFileGateway gateway = new RootFileGateway(context);
        RootTransactionGuard guard = null;
        String stage = "lock";
        try {
            guard = RootTransactionGuard.acquire(context, "edge", "tabs-read");
            guard.requireNoUnknownState();
            stage = "root";
            gateway.requireRoot();
            int userId = currentUserId();
            stage = "stop";
            gateway.forceStop(EDGE_PACKAGE, userId);
            stage = "resolve-profile";
            String profile = resolveEdgeProfile(gateway, userId);
            if (profile == null) throw new IOException("未找到 Edge 默认配置目录");
            stage = "resolve-session";
            String tabsDirectory = gateway.resolveFirstExistingDirectory(
                    profile + "/tabs", profile + "/Tabs");
            if (tabsDirectory == null) throw new IOException("未找到 Edge 标签页会话目录");
            Map<String, CanonicalOpenTab> tabs = new LinkedHashMap<>();
            int sequence = 0;
            // tab_state0, tab_state1, ... represent simultaneous tabbed-mode
            // selectors/windows, not generations of one file. Read every model.
            stage = "scan-session";
            for (String path : gateway.findFilesByNamePattern(
                    tabsDirectory, "tab_state[0-9]*", 3, 32)) {
                if (!isRegularTabStatePath(tabsDirectory, path)) continue;
                long savedAt = gateway.lastModifiedMillis(path);
                stage = "snapshot-session";
                File snapshot = gateway.snapshotFile(
                        path, "edge-tabs", "tab-state-" + (++sequence) + ".bin");
                stage = "parse-session";
                try (FileInputStream input = new FileInputStream(snapshot)) {
                    for (ChromiumTabMetadataReader.Entry entry : ChromiumTabMetadataReader.read(input)) {
                        String url = entry.getUrl();
                        tabs.putIfAbsent(url, new CanonicalOpenTab(
                                fallbackTitle(url), url, savedAt, savedAt, false));
                        if (tabs.size() >= MAX_TABS) break;
                    }
                }
                if (tabs.size() >= MAX_TABS) break;
            }
            stage = "titles";
            Map<String, String> titles = readEdgeTitlesIfAvailable(gateway, profile, tabs.keySet());
            for (Map.Entry<String, String> title : titles.entrySet()) {
                CanonicalOpenTab current = tabs.get(title.getKey());
                if (current == null || title.getValue() == null || title.getValue().trim().isEmpty()) continue;
                tabs.put(title.getKey(), new CanonicalOpenTab(
                        title.getValue(), current.getUrl(), current.getOpenedAtEpochMillis(),
                        current.getLastActiveAtEpochMillis(), current.getPinned()));
            }
            List<CanonicalOpenTab> result = new ArrayList<>(tabs.values());
            LogHelper.v("OpenTabsMeta", "browser=EDGE stage=complete count=" + result.size());
            return result;
        } catch (IOException error) {
            LogHelper.e("OpenTabsMeta", "browser=EDGE stage=" + stage
                    + " type=" + error.getClass().getSimpleName());
            throw error;
        } catch (RuntimeException error) {
            LogHelper.e("OpenTabsMeta", "browser=EDGE stage=" + stage
                    + " type=" + error.getClass().getSimpleName());
            throw new IOException("Edge 标签页读取在 " + stage + " 阶段失败", error);
        } finally {
            closeGuard(guard);
        }
    }

    private static String resolveViaDatabase(RootFileGateway gateway, int userId)
            throws IOException {
        String user = "/data/user/" + userId + "/" + VIA_PACKAGE;
        String mirror = "/data_mirror/data_ce/null/" + userId + "/" + VIA_PACKAGE;
        String legacyMirror = "/data_mirror/data_ce/" + userId + "/" + VIA_PACKAGE;
        return gateway.resolveFirstExistingFile(
                user + "/databases/via",
                "/data/data/" + VIA_PACKAGE + "/databases/via",
                mirror + "/databases/via",
                legacyMirror + "/databases/via");
    }

    private static String resolveEdgeProfile(RootFileGateway gateway, int userId)
            throws IOException {
        String user = "/data/user/" + userId + "/" + EDGE_PACKAGE;
        String compatibility = "/data/data/" + EDGE_PACKAGE;
        String mirror = "/data_mirror/data_ce/null/" + userId + "/" + EDGE_PACKAGE;
        String legacyMirror = "/data_mirror/data_ce/" + userId + "/" + EDGE_PACKAGE;
        return gateway.resolveFirstExistingDirectory(
                user + "/app_chrome/Default",
                compatibility + "/app_chrome/Default",
                user + "/app_edge/Default",
                user + "/app_chromium/Default",
                mirror + "/app_chrome/Default",
                mirror + "/app_edge/Default",
                mirror + "/app_chromium/Default",
                legacyMirror + "/app_chrome/Default");
    }

    private static List<CanonicalOpenTab> readViaSnapshot(File snapshot) throws IOException {
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            List<CanonicalOpenTab> result = new ArrayList<>();
            try (Cursor cursor = database.rawQuery(
                    "SELECT url,title,last_visited_at,flags FROM tabs "
                            + "WHERE (flags & 2) != 0 "
                            + "ORDER BY last_visited_at DESC LIMIT ?",
                    new String[] { String.valueOf(MAX_TABS) })) {
                int urlColumn = cursor.getColumnIndexOrThrow("url");
                int titleColumn = cursor.getColumnIndexOrThrow("title");
                int visitedColumn = cursor.getColumnIndexOrThrow("last_visited_at");
                int flagsColumn = cursor.getColumnIndexOrThrow("flags");
                while (cursor.moveToNext()) {
                    String url = cursor.getString(urlColumn);
                    if (!isWebUrl(url)) continue;
                    String title = cursor.isNull(titleColumn) ? "" : cursor.getString(titleColumn);
                    long activeAt = normalizeEpochMillis(cursor.getLong(visitedColumn));
                    int flags = cursor.isNull(flagsColumn) ? 0 : cursor.getInt(flagsColumn);
                    if (!ViaTabFlags.isOpen(flags)) continue;
                    result.add(new CanonicalOpenTab(
                            title == null || title.trim().isEmpty() ? fallbackTitle(url) : title,
                            url, activeAt, activeAt, false));
                }
            }
            return result;
        } catch (RuntimeException error) {
            throw new IOException("Via 标签页数据库结构不受支持", error);
        } finally {
            if (database != null) database.close();
        }
    }

    private static Map<String, String> readEdgeTitlesIfAvailable(
            RootFileGateway gateway, String profile, Iterable<String> tabUrls) {
        Map<String, String> result = new HashMap<>();
        try {
            List<String> urls = new ArrayList<>();
            for (String url : tabUrls) urls.add(url);
            if (urls.isEmpty()) return result;
            String path = gateway.resolveFirstExistingFile(profile + "/History");
            if (path == null) return result;
            File snapshot = gateway.snapshotSqliteDatabase(path, "edge-tabs", "History");
            try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                    snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
                for (int start = 0; start < urls.size(); start += 400) {
                    List<String> chunk = urls.subList(start, Math.min(start + 400, urls.size()));
                    StringBuilder placeholders = new StringBuilder();
                    for (int index = 0; index < chunk.size(); index++) {
                        if (index > 0) placeholders.append(',');
                        placeholders.append('?');
                    }
                    try (Cursor cursor = database.rawQuery(
                            "SELECT url,title FROM urls WHERE url IN (" + placeholders + ")",
                            chunk.toArray(new String[0]))) {
                        int urlColumn = cursor.getColumnIndexOrThrow("url");
                        int titleColumn = cursor.getColumnIndexOrThrow("title");
                        while (cursor.moveToNext()) {
                            if (!cursor.isNull(titleColumn)) {
                                result.put(cursor.getString(urlColumn), cursor.getString(titleColumn));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Titles improve presentation only; session URLs remain independently readable.
        }
        return result;
    }

    private static boolean isRegularTabStatePath(String base, String path) {
        if (path == null || !path.startsWith(base + "/")) return false;
        String relative = path.substring(base.length() + 1);
        return relative.matches("[0-9]+/tab_state[0-9]+");
    }

    private static boolean isWebUrl(String value) {
        return value != null && (value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8));
    }

    private static String fallbackTitle(String url) {
        String host = Uri.parse(url).getHost();
        return host == null || host.trim().isEmpty() ? url : host;
    }

    private static long normalizeEpochMillis(long value) {
        if (value <= 0L) return System.currentTimeMillis();
        return value < 1_000_000_000_000L ? value * 1000L : value;
    }

    private static int currentUserId() { return Process.myUid() / PER_USER_RANGE; }

    private static void closeGuard(RootTransactionGuard guard) throws IOException {
        if (guard != null) guard.close();
    }
}
