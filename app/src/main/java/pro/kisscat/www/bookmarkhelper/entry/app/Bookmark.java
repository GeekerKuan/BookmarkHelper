package pro.kisscat.www.bookmarkhelper.entry.app;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/10/10
 * Time:14:21
 */

public class Bookmark {
    private String title;
    private String url;
    private String folder;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

//    public boolean equals(Object anObject) {
//        if (this == anObject) {
//            return true;
//        }
//        if (anObject instanceof Bookmark) {
//            Bookmark anotherBookmark = (Bookmark) anObject;
//            if (getUrl() == anotherBookmark.getUrl()) {
//                return true;
//            }
//        }
//        return false;
//    }
}
