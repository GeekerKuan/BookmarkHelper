package pro.kisscat.www.bookmarkhelper.exception;

import pro.kisscat.www.bookmarkhelper.common.shared.MetaData;
import pro.kisscat.www.bookmarkhelper.util.appList.AppListUtil;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/11/8
 * Time:14:26
 */

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    // 需求是 整个应用程序 只有一个 MyCrash-Handler
    private static CrashHandler INSTANCE;
    private Thread.UncaughtExceptionHandler previousHandler;

    //1.私有化构造方法
    private CrashHandler() {
    }

    public static synchronized CrashHandler getInstance() {
        if (INSTANCE == null)
            INSTANCE = new CrashHandler();
        return INSTANCE;
    }

    public void init() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current != this) {
            previousHandler = current;
        }
    }


    public void uncaughtException(Thread thread, Throwable throwable) {
        // Exception messages can contain bookmark rows, URLs or database paths.
        // Record only the public exception type; LogHelper adds a bounded,
        // message-free structural stack summary below.
        String exceptionType = throwable == null
                ? "null"
                : throwable.getClass().getName();
        String fatalErrorMessage = MetaData.LOG_E_FATAL + ":" + AppListUtil.thisAppInfo
                + " crashed; exception type:" + exceptionType;
        try {
            LogHelper.e(MetaData.LOG_E_FATAL, fatalErrorMessage);
            LogHelper.e(MetaData.LOG_E_FATAL, throwable);
            LogHelper.writeNow();
        } catch (RuntimeException ignored) {
            // Never replace the original crash with a logging failure.
        }
        if (previousHandler != null) {
            previousHandler.uncaughtException(thread, throwable);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
