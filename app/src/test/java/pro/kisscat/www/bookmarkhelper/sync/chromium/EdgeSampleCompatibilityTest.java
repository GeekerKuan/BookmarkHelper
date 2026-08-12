package pro.kisscat.www.bookmarkhelper.sync.chromium;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;

import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;

/** Optional local compatibility check; no sample contents or paths are logged. */
public class EdgeSampleCompatibilityTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validatesPrivateEdgeSampleWhenProvided() throws Exception {
        String path = System.getenv("BOOKMARKHELPER_EDGE_SAMPLE");
        Assume.assumeTrue(path != null && !path.trim().isEmpty());
        ChromiumBookmarksFile.validate(new File(path));
    }

    @Test
    public void mergesFakeBookmarkIntoPrivateSampleCopyWhenProvided() throws Exception {
        String path = System.getenv("BOOKMARKHELPER_EDGE_SAMPLE");
        Assume.assumeTrue(path != null && !path.trim().isEmpty());
        Bookmark fake = new Bookmark();
        fake.setTitle("BookmarkHelper compatibility probe");
        fake.setUrl("https://bookmarkhelper.invalid/compatibility-probe");
        fake.setFolder("Compatibility");
        File destination = temporaryFolder.newFile("Bookmarks.updated");
        ChromiumBookmarksFile.MergeResult result = ChromiumBookmarksFile.merge(
                new File(path), destination, Collections.singletonList(fake));
        org.junit.Assert.assertEquals(1, result.getImported());
        ChromiumBookmarksFile.validate(destination);
    }
}
