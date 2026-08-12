package pro.kisscat.www.bookmarkhelper.sync.root;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootFileGatewayTest {
    @Test
    public void acceptsTraditionalAndAndroidDataMirrorPaths() {
        assertTrue(RootFileGateway.isSafeBrowserPrivatePath(
                "/data/user/0/com.microsoft.emmx/app_chrome/Default/tabs"));
        assertTrue(RootFileGateway.isSafeBrowserPrivatePath(
                "/data_mirror/data_ce/null/0/com.microsoft.emmx/app_chrome/Default/tabs"));
    }

    @Test
    public void rejectsTraversalAndUnrelatedRoots() {
        assertFalse(RootFileGateway.isSafeBrowserPrivatePath("/data/user/0/pkg/../other"));
        assertFalse(RootFileGateway.isSafeBrowserPrivatePath("/sdcard/browser/tabs"));
        assertFalse(RootFileGateway.isSafeBrowserPrivatePath(null));
    }
}
