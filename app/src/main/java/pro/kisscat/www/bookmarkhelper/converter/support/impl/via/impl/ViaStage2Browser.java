package pro.kisscat.www.bookmarkhelper.converter.support.impl.via.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import pro.kisscat.www.bookmarkhelper.converter.support.impl.via.ViaBrowserAble;
import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;
import pro.kisscat.www.bookmarkhelper.database.SQLite.DBHelper;
import pro.kisscat.www.bookmarkhelper.exception.ConverterException;
import pro.kisscat.www.bookmarkhelper.sync.root.RootFileGateway;
import pro.kisscat.www.bookmarkhelper.util.Path;
import pro.kisscat.www.bookmarkhelper.util.context.ContextUtil;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/11/14
 * Time:13:18
 * <p>
 * versionName>=2.1.1  && versionCode>=20161113
 * <p>
 * stage2较stage1主要是书签存取由json txt转变为sqlite3
 */

public class ViaStage2Browser extends ViaBrowserAble {
    private static final String TAG = "ViaStage2";
    private static final int MAX_VIA_BOOKMARKS = 50_000;
    public static final long minVersionCode = 20161113L;
    private static final String fileName_origin = "via";
    private static final String databaseDirPath_origin = Path.INNER_PATH_DATA + packageName + Path.FILE_SPLIT + "databases" + Path.FILE_SPLIT;

    @Override
    public List<Bookmark> readBookmark() {
        // Via may have changed since the previous synchronization in this process.
        // Never reuse a prior snapshot across runs.
        bookmarks = null;
        LogHelper.v(TAG + ":开始读取书签数据");
        try {
            Context context = ContextUtil.getApplicationContext();
            RootFileGateway gateway = new RootFileGateway(context);
            gateway.requireRoot();
            LogHelper.v(TAG + ":正在停止 Via 进程");
            gateway.forceStop(packageName);
            LogHelper.v(TAG + ":Via 进程已停止，正在定位数据库");
            // Android assigns each user a range of 100,000 UIDs (PER_USER_RANGE).
            // The range constant is hidden, but deriving the current user from our
            // public process UID avoids relying on UserHandle#getIdentifier().
            int userId = Process.myUid() / 100000;
            String userDatabase = "/data/user/" + userId + "/" + packageName
                    + "/databases/" + fileName_origin;
            // KernelSU-compatible root profiles can expose CE app data through
            // Android's data_mirror namespace while hiding the canonical
            // /data/user path from the granted mount namespace. Probe only the
            // exact, package-scoped database path; never scan unrelated data.
            String mirroredDatabase = "/data_mirror/data_ce/null/" + userId + "/"
                    + packageName + "/databases/" + fileName_origin;
            String legacyMirroredDatabase = "/data_mirror/data_ce/" + userId + "/"
                    + packageName + "/databases/" + fileName_origin;
            String originFilePath = userId == 0
                    ? gateway.resolveFirstExistingFile(
                            userDatabase,
                            databaseDirPath_origin + fileName_origin,
                            mirroredDatabase,
                            legacyMirroredDatabase)
                    : gateway.resolveFirstExistingFile(
                            userDatabase,
                            mirroredDatabase,
                            legacyMirroredDatabase);
            LogHelper.v(TAG + ":origin file path:" + originFilePath);
            if (originFilePath == null) {
                throw new ConverterException(ContextUtil.buildViaBookmarksFileMiss(this.getName()));
            }
            File snapshot = gateway.snapshotSqliteDatabase(originFilePath, "via", fileName_origin);
            LogHelper.v(TAG + ":数据库快照完成，正在识别收藏表结构");
            List<Bookmark> bookmarksList = fetchBookmarksList(snapshot.getAbsolutePath());
            bookmarks = new LinkedList<>();
            fetchValidBookmarks(bookmarks, bookmarksList);
        } catch (ConverterException converterException) {
            LogHelper.e(converterException);
            throw converterException;
        } catch (Exception e) {
            LogHelper.e(e);
            String detail = e.getMessage();
            if (detail == null || detail.trim().isEmpty()) {
                detail = e.getClass().getSimpleName();
            }
            throw new ConverterException(
                    ContextUtil.buildReadBookmarksErrorMessage(this.getName()) + "：" + detail);
        } finally {
            LogHelper.v(TAG + ":读取书签数据结束");
        }
        return bookmarks;
    }

    private static final String LEGACY_TABLE = "bookmarks";
    private static final String MODERN_ITEMS_TABLE = "bookmark_items";
    private static final String MODERN_FOLDERS_TABLE = "bookmark_folders";

    private List<Bookmark> fetchBookmarksList(String dbFilePath) {
        LogHelper.v(TAG + ":开始读取书签SQLite数据库:" + dbFilePath);
        List<Bookmark> result = new LinkedList<>();
        SQLiteDatabase sqLiteDatabase = null;
        Cursor cursor = null;
        boolean tableExist;
        try {
            sqLiteDatabase = DBHelper.openReadOnlyDatabase(dbFilePath);
            requireDatabaseIntegrity(sqLiteDatabase);
            boolean modernSchema = DBHelper.checkTableExist(sqLiteDatabase, MODERN_ITEMS_TABLE);
            tableExist = modernSchema || DBHelper.checkTableExist(sqLiteDatabase, LEGACY_TABLE);
            if (!tableExist) {
                LogHelper.v(TAG + ":no supported bookmark table exists.");
                throw new ConverterException(ContextUtil.buildReadBookmarksTableNotExistErrorMessage(this.getName()));
            }
            cursor = modernSchema
                    ? queryModernBookmarks(sqLiteDatabase)
                    : queryLegacyBookmarks(sqLiteDatabase);
            if (cursor != null && cursor.getCount() > 0) {
                if (cursor.getCount() > MAX_VIA_BOOKMARKS) {
                    throw new ConverterException("Via 书签数量超过安全上限，已停止导入");
                }
                while (cursor.moveToNext()) {
                    Bookmark item = new Bookmark();
                    item.setUrl(cursor.getString(cursor.getColumnIndexOrThrow("url")));
                    item.setTitle(cursor.getString(cursor.getColumnIndexOrThrow("title")));
                    item.setFolder(cursor.getString(cursor.getColumnIndexOrThrow("folder")));
                    result.add(item);
                }
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (sqLiteDatabase != null) {
                sqLiteDatabase.close();
            }
            LogHelper.v(TAG + ":读取书签SQLite数据库结束");
        }
        return result;
    }

    /** Via 6.7+ stores bookmarks and folders in separate text-keyed tables. */
    private Cursor queryModernBookmarks(SQLiteDatabase database) {
        boolean foldersExist = DBHelper.checkTableExist(database, MODERN_FOLDERS_TABLE);
        String folderExpression = foldersExist
                ? "COALESCE(f.title, '')"
                : "''";
        String join = foldersExist
                ? " LEFT JOIN bookmark_folders f ON f._id = i.folder_id"
                : "";
        String sql = "SELECT i.url AS url, i.title AS title, "
                + folderExpression + " AS folder FROM bookmark_items i"
                + join
                + " WHERE i.url IS NOT NULL AND i.url <> ''"
                + " GROUP BY i.url"
                + " ORDER BY i.ordering ASC, i.created_at ASC"
                + " LIMIT " + (MAX_VIA_BOOKMARKS + 1);
        LogHelper.v(TAG + ":using Via bookmark_items schema");
        return database.rawQuery(sql, null);
    }

    private Cursor queryLegacyBookmarks(SQLiteDatabase database) {
        LogHelper.v(TAG + ":using legacy Via bookmarks schema");
        return database.query(
                false,
                LEGACY_TABLE,
                new String[]{"id", "url", "title", "folder"},
                null,
                null,
                "url",
                null,
                "id asc",
                Integer.toString(MAX_VIA_BOOKMARKS + 1));
    }

    private void requireDatabaseIntegrity(SQLiteDatabase database) {
        if (database == null) {
            throw new ConverterException("无法打开 Via 数据库快照");
        }
        Cursor integrity = null;
        try {
            integrity = database.rawQuery("PRAGMA quick_check", null);
            if (!integrity.moveToFirst()
                    || !"ok".equalsIgnoreCase(integrity.getString(0))) {
                throw new ConverterException("Via 数据库快照完整性检查未通过，已停止导入");
            }
        } finally {
            if (integrity != null) {
                integrity.close();
            }
        }
    }

    @Override
    public int appendBookmark(List<Bookmark> appends) {
        throw new ConverterException("现代分支仅允许从 Via 读取，不允许写入 Via");
    }
}
