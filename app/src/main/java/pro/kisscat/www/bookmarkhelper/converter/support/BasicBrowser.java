package pro.kisscat.www.bookmarkhelper.converter.support;

import android.content.Context;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.Setter;
import pro.kisscat.www.bookmarkhelper.common.shared.MetaData;
import pro.kisscat.www.bookmarkhelper.entry.app.App;
import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;
import pro.kisscat.www.bookmarkhelper.util.Path;
import pro.kisscat.www.bookmarkhelper.util.appList.AppListUtil;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/10/9
 * Time:15:31
 */
public class BasicBrowser extends App implements BrowserAble {
    @Setter
    protected int bookmarkSum;
    @Setter
    protected boolean installed;

    public boolean isInstalled(Context context, BasicBrowser browser) {
        setInstalled(AppListUtil.isInstalled(context, browser.getPackageName()));
        return installed;
    }

    public void fillName(Context context) {
        if (installed) {
            super.setName(AppListUtil.getAppName(context, getPackageName()));
        }
        fillDefaultAppName(context);
    }

    public void fillVersion(Context context) {
        if (installed) {
            App app = AppListUtil.getAppInfo(context, getPackageName());
            if (app != null) {
                super.setVersionName(app.getVersionName());
                super.setVersionCode(app.getVersionCode());
            }
        }
    }

    @Override
    public void fillDefaultAppName(Context context) {

    }

    @Override
    public int readBookmarkSum() {
        return 0;
    }

    @Override
    public void fillDefaultIcon(Context context) {

    }

    @Override
    public String getPackageName() {
        return null;
    }


    @Override
    public List<Bookmark> readBookmark() {
        return null;
    }

    @Override
    public int appendBookmark(List<Bookmark> bookmarks) {
        return 0;
    }

    protected Set<Bookmark> buildNoRepeat(List<Bookmark> appends, List<Bookmark> exists) {
        Set<String> appendURLs = new HashSet<>();
        Set<Bookmark> noRepeatAppends = new LinkedHashSet<>();
        for (Bookmark item : appends) {
            String url = item.getUrl();
            if (url != null && !url.isEmpty() && !appendURLs.contains(url)) {
                appendURLs.add(url);
                noRepeatAppends.add(item);
            }
        }
        Set<String> existURLs = new HashSet<>();
        for (Bookmark item : exists) {
            existURLs.add(item.getUrl());
        }
        Set<Bookmark> result = new LinkedHashSet<>();
        for (Bookmark item : noRepeatAppends) {
            if (!existURLs.contains(item.getUrl())) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean isValidUrl(String bookmarkUrl) {
        if (bookmarkUrl == null) {
            return false;
        }
        String normalized = bookmarkUrl.trim().toLowerCase(Locale.US);
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))
                || normalized.endsWith("://")) {
            LogHelper.v("检测到无效网址，已跳过（日志不会记录网址内容）");
            return false;
        }
        return true;
    }

    protected String trim(String folderPath) {
        if (folderPath != null && folderPath.endsWith(Path.FILE_SPLIT)) {
            folderPath = folderPath.substring(0, folderPath.length() - 1);
        }
        return folderPath;
    }

    protected void fetchValidBookmarks(List<Bookmark> target, List<Bookmark> source) {
        if (target == null) {
            return;
        } else if (!target.isEmpty()) {
            target.clear();
        }
        if (source == null || source.isEmpty()) {
            return;
        }
        LogHelper.v("读取到的书签条数:" + source.size());
        int size = source.size();
        for (Bookmark item : source) {
            String bookmarkUrl = item.getUrl() == null ? null : item.getUrl().trim();
            String bookmarkFolder = item.getFolder();
            String bookmarkTitle = item.getTitle();
            if (!isValidUrl(bookmarkUrl)) {
                continue;
            }
            if (bookmarkTitle == null || bookmarkTitle.isEmpty()) {
                LogHelper.v("检测到空标题，已使用默认标题");
                bookmarkTitle = MetaData.BOOKMARK_TITLE_DEFAULT;
            }
            Bookmark bookmark = new Bookmark();
            bookmark.setTitle(bookmarkTitle);
            bookmark.setUrl(bookmarkUrl);
            if (!(bookmarkFolder == null || bookmarkFolder.isEmpty())) {
                bookmark.setFolder(bookmarkFolder);
            }
            target.add(bookmark);
        }
        setBookmarkSum(target.size());
        LogHelper.v("有效书签条数:" + target.size() + "/" + size);
    }

    protected String getFileNameByTrimPath(String dir, String fileFullName) {
        if (fileFullName == null || fileFullName.isEmpty()) {
            return fileFullName;
        }
        if (fileFullName.startsWith(dir)) {
            return fileFullName.replace(dir, "");
        }
        return fileFullName;
    }

    public String getPreExecuteConverterMessage() {
        return null;
    }
}
