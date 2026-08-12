package pro.kisscat.www.bookmarkhelper.converter.support.impl.via;

import android.content.Context;
import androidx.core.content.ContextCompat;

import java.util.List;

import pro.kisscat.www.bookmarkhelper.R;
import pro.kisscat.www.bookmarkhelper.converter.support.BasicBrowser;
import pro.kisscat.www.bookmarkhelper.converter.support.impl.via.impl.ViaStage2Browser;
import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/11/14
 * Time:13:20
 */

public class ViaBrowserAble extends BasicBrowser {
    public static final String packageName = "mark.via";
    protected List<Bookmark> bookmarks;

    public static ViaBrowserAble fetchViaBrowser() {
        // This Android 12+ branch intentionally supports only Via's SQLite format.
        // The pre-2016 text format depended on shared-storage root copies and is
        // deliberately unreachable from the modern sync rule.
        return new ViaStage2Browser();
    }

    public String getPackageName() {
        return packageName;
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
        this.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_via));
    }

    @Override
    public void fillDefaultAppName(Context context) {
        this.setName(context.getString(R.string.browser_name_show_via));
    }
}
