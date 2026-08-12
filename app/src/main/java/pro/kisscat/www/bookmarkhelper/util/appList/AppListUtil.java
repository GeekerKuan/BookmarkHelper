package pro.kisscat.www.bookmarkhelper.util.appList;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Map;
import java.util.TreeMap;

import pro.kisscat.www.bookmarkhelper.R;
import pro.kisscat.www.bookmarkhelper.entry.app.App;
import pro.kisscat.www.bookmarkhelper.exception.InitException;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/10/10
 * Time:10:47
 */

public class AppListUtil {
    private static final String[] REQUIRED_VISIBLE_PACKAGES = {
            "mark.via",
            "com.microsoft.emmx"
    };
    private static Map<String, App> installedAllApp;
    public static String thisAppInfo;
    public static String thisAppPackageName;

    private static Map<String, App> getInstalledAllApp(Context context) {
        if (installedAllApp == null) {
            init(context);
        }
        return installedAllApp;
    }

    public static void reInit(Context context) {
        LogHelper.v("AppListUtil reInit begining.");
        installedAllApp = null;
        init(context);
        LogHelper.v("AppListUtil reInit completed.");
    }

    public static void init(Context context) {
        LogHelper.v("AppListUtil init begining.");
        String globalMsg = context.getResources().getString(R.string.notPermissionForReadInstalledAppList);
        if (installedAllApp != null) {
            LogHelper.v("AppListUtil init is not necessary.");
            return;
        }
        installedAllApp = new TreeMap<>();
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            LogHelper.v("AppListUtil init failure,context.getPackageManager is null.");
            throw new InitException(globalMsg);
        }
        String mePackageName = context.getPackageName();
        thisAppPackageName = mePackageName;
        addPackage(packageManager, mePackageName, context, true);
        for (String packageName : REQUIRED_VISIBLE_PACKAGES) {
            addPackage(packageManager, packageName, context, false);
        }
        if (installedAllApp.isEmpty()) {
            throw new InitException(globalMsg);
        }
        LogHelper.v("AppListUtil init success.");
    }

    private static void addPackage(PackageManager packageManager, String packageName,
                                   Context context, boolean isCurrentApp) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= 33) {
                packageInfo = packageManager.getPackageInfo(
                        packageName, PackageManager.PackageInfoFlags.of(0L));
            } else {
                // Kept for Android 12 support.
                //noinspection deprecation
                packageInfo = packageManager.getPackageInfo(packageName, 0);
            }
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            App app = new App();
            app.setName(applicationInfo.loadLabel(packageManager).toString());
            app.setPackageName(packageInfo.packageName);
            if (isCurrentApp && thisAppInfo == null) {
                thisAppInfo = "App name:" + context.getString(R.string.app_name)
                        + ",packageName:" + packageName
                        + ",versionName:" + packageInfo.versionName
                        + ",versionCode:" + packageInfo.getLongVersionCode();
                LogHelper.v(thisAppInfo);
            }
            app.setVersionName(packageInfo.versionName);
            app.setVersionCode(packageInfo.getLongVersionCode());
            installedAllApp.put(app.getPackageName(), app);
        } catch (PackageManager.NameNotFoundException notInstalled) {
            if (isCurrentApp) {
                throw new InitException("无法读取当前应用信息");
            }
        }
    }

    public static boolean isInstalled(Context context, String packageName) {
        return getInstalledAllApp(context).keySet().contains(packageName);
    }

//    public static Drawable getIcon(String packageName) {
//        if (isInstalled(packageName)) {
//            return installedAllApp.get(packageName).getIcon();
//        }
//        return null;
//    }

    public static String getAppName(Context context, String packageName) {
        if (isInstalled(context, packageName)) {
            return getInstalledAllApp(context).get(packageName).getName();
        }
        return "ERROR";
    }

    public static App getAppInfo(Context context, String packageName) {
        if (isInstalled(context, packageName)) {
            return getInstalledAllApp(context).get(packageName);
        }
        return null;
    }

    public static App getAppInfo(String packageName) {
        if (installedAllApp != null) {
            return installedAllApp.get(packageName);
        }
        return null;
    }
}
