package pro.kisscat.www.bookmarkhelper.xposed;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * Narrow proof-of-concept for Via 6.7.1 and Edge 150.
 *
 * It deliberately reacts only to the public example.com marker used by the in-app test page.
 * Normal browsing, stored history, cookies and browser files are not read or modified.
 */
public final class ViaEdgeBridgeModule extends XposedModule {
    private static final String TAG = "BookmarkBridge";
    private static final String VIA_PACKAGE = "mark.via";
    private static final String EDGE_PACKAGE = "com.microsoft.emmx";
    private static final String VIA_671_WEB_CLIENT = "d.h.a.c.d";
    private static final String TEST_HOST = "example.com";
    private static final String TEST_PARAMETER = "bookmarkhelper_lsposed_test";
    private static final AtomicBoolean TEST_FORWARDED = new AtomicBoolean(false);

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        if (VIA_PACKAGE.equals(param.getPackageName())) {
            installViaTestHook(param.getClassLoader());
        } else if (EDGE_PACKAGE.equals(param.getPackageName())) {
            probeEdge150(param.getClassLoader());
        }
    }

    private void installViaTestHook(ClassLoader classLoader) {
        try {
            Class<?> webClient = Class.forName(VIA_671_WEB_CLIENT, false, classLoader);
            Method pageFinished = webClient.getDeclaredMethod(
                    "onPageFinished", WebView.class, String.class);
            hook(pageFinished).intercept(chain -> {
                Object result = chain.proceed();
                Object rawUrl = chain.getArg(1);
                Object rawView = chain.getArg(0);
                if (rawUrl instanceof String && rawView instanceof WebView) {
                    forwardMarkedTest((WebView) rawView, (String) rawUrl);
                }
                return result;
            });
            log(Log.INFO, TAG, "Via 6.7.1 test hook ready");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Via adapter is incompatible; no hook installed", error);
        }
    }

    private void forwardMarkedTest(WebView webView, String rawUrl) {
        Uri url;
        try {
            url = Uri.parse(rawUrl);
        } catch (RuntimeException ignored) {
            return;
        }
        if (!"https".equalsIgnoreCase(url.getScheme())
                || !TEST_HOST.equalsIgnoreCase(url.getHost())
                || !"1".equals(url.getQueryParameter(TEST_PARAMETER))
                || !TEST_FORWARDED.compareAndSet(false, true)) {
            return;
        }

        try {
            Context context = webView.getContext().getApplicationContext();
            Intent edge = new Intent(Intent.ACTION_VIEW, url)
                    .setPackage(EDGE_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(edge);
            log(Log.INFO, TAG, "Marked test page handed to Edge");
        } catch (Throwable error) {
            TEST_FORWARDED.set(false);
            log(Log.ERROR, TAG, "Could not hand marked test page to Edge", error);
        }
    }

    private void probeEdge150(ClassLoader classLoader) {
        String[] required = {
                "org.chromium.chrome.browser.ChromeTabbedActivity",
                "org.chromium.chrome.browser.tabmodel.TabModelJniBridge",
                "org.chromium.content_public.browser.LoadUrlParams",
        };
        try {
            for (String name : required) Class.forName(name, false, classLoader);
            log(Log.INFO, TAG, "Edge 150 tab classes detected; probe only");
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Edge tab classes differ from the tested build", error);
        }
    }
}
