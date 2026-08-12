package pro.kisscat.www.bookmarkhelper.sync.chromium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;

public class ChromiumBookmarksFileTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void mergePreservesUnknownFieldsAndAddsOnlyNewUrls() throws Exception {
        File source = temporaryFolder.newFile("Bookmarks");
        File destination = temporaryFolder.newFile("Bookmarks.updated");
        Files.write(source.toPath(), fixture().toString(2).getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.MergeResult result = ChromiumBookmarksFile.merge(
                source,
                destination,
                Arrays.asList(
                        bookmark("Existing duplicate", "https://existing.example", "Old"),
                        bookmark("Via page", "https://via.example/page", "News/Android")));

        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkipped());
        assertEquals(2, result.getTotalUniqueUrls());

        JSONObject output = read(destination);
        assertTrue(output.getJSONObject("unknown_top").getBoolean("keep"));
        assertEquals("opaque-sync-metadata", output.getString("sync_metadata"));
        assertEquals(32, output.getString("checksum").length());
        assertFalse(output.has("checksum_sha256"));

        JSONObject synced = output.getJSONObject("roots").getJSONObject("synced");
        JSONObject existing = synced.getJSONArray("children").getJSONObject(0);
        assertEquals("keep-me", existing.getString("custom_edge_field"));

        JSONObject viaImport = findFolder(synced.getJSONArray("children"), "Via Import");
        assertNotNull(viaImport);
        JSONObject news = findFolder(viaImport.getJSONArray("children"), "News");
        assertNotNull(news);
        JSONObject android = findFolder(news.getJSONArray("children"), "Android");
        assertNotNull(android);
        JSONObject imported = android.getJSONArray("children").getJSONObject(0);
        assertEquals("Via page", imported.getString("name"));
        assertEquals("https://via.example/page", imported.getString("url"));
        assertEquals("url", imported.getString("type"));
        assertEquals("unknown", imported.getString("source"));
        assertFalse(imported.getBoolean("show_icon"));
        assertEquals(0, imported.getInt("visit_count"));
        assertTrue(imported.getString("guid").matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    @Test
    public void duplicateOnlyMergeDoesNotCreateAnEmptyImportFolder() throws Exception {
        File source = temporaryFolder.newFile("Bookmarks-duplicates");
        File destination = temporaryFolder.newFile("Bookmarks-duplicates.updated");
        JSONObject original = fixture();
        String originalChecksum = original.getString("checksum");
        Files.write(source.toPath(), original.toString(2).getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.MergeResult result = ChromiumBookmarksFile.merge(
                source,
                destination,
                Collections.singletonList(
                        bookmark("Existing duplicate", "https://existing.example", null)));

        assertEquals(0, result.getImported());
        assertEquals(1, result.getSkipped());
        JSONObject output = read(destination);
        JSONArray children = output.getJSONObject("roots")
                .getJSONObject("synced")
                .getJSONArray("children");
        assertFalse(hasFolder(children, "Via Import"));
        assertEquals(originalChecksum, output.getString("checksum"));
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsUnknownBookmarksVersion() throws Exception {
        File source = temporaryFolder.newFile("Bookmarks-v2");
        File destination = temporaryFolder.newFile("Bookmarks-v2.updated");
        JSONObject unsupported = fixture().put("version", 2);
        Files.write(source.toPath(), unsupported.toString().getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.merge(source, destination, Collections.emptyList());
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsAFileWhoseContentsDoNotMatchItsChecksum() throws Exception {
        File source = temporaryFolder.newFile("Bookmarks-corrupt");
        File destination = temporaryFolder.newFile("Bookmarks-corrupt.updated");
        JSONObject corrupt = fixture();
        corrupt.getJSONObject("roots")
                .getJSONObject("synced")
                .getJSONArray("children")
                .getJSONObject(0)
                .put("name", "Changed without updating checksum");
        Files.write(source.toPath(), corrupt.toString().getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.merge(source, destination, Collections.emptyList());
    }

    @Test
    public void validatesFixedChromiumChecksumVectorWithUtf16AndSurrogatePairs()
            throws Exception {
        File source = temporaryFolder.newFile("Bookmarks-fixed-vector");
        JSONObject roots = new JSONObject();
        JSONObject unicodeUrl = new JSONObject()
                .put("children_unused", "preserved")
                .put("guid", "44444444-4444-4444-8444-444444444444")
                .put("id", "4")
                .put("name", "例子🚀")
                .put("type", "url")
                .put("url", "https://example.com/%E8%B7%AF%E5%BE%84");
        roots.put("bookmark_bar", folderNode(
                "1", "收藏😀", new JSONArray().put(unicodeUrl)));
        roots.put("other", folderNode("2", "Other", new JSONArray()));
        roots.put("synced", folderNode("3", "Mobile", new JSONArray()));

        // Fixed values are independently derived from Chromium's documented
        // byte sequence: id UTF-8, title UTF-16LE, type UTF-8, then URL UTF-8.
        JSONObject document = new JSONObject()
                .put("checksum", "0e94625357970ff3d3bc55d74ae06805")
                .put("checksum_sha256",
                        "5033621592ec540e6c409c48bd224e20df64778e5a96684b29de12d2be957490")
                .put("roots", roots)
                .put("version", 1);
        Files.write(source.toPath(), document.toString(2).getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.validate(source);
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsDuplicateNodeIdsEvenWhenChecksumMatches() throws Exception {
        File source = temporaryFolder.newFile("Bookmarks-duplicate-ids");
        JSONObject duplicate = fixture();
        duplicate.getJSONObject("roots")
                .getJSONObject("synced")
                .getJSONArray("children")
                .getJSONObject(0)
                .put("id", "2");
        ChromiumBookmarksFile.refreshChecksums(duplicate);
        Files.write(source.toPath(), duplicate.toString().getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.validate(source);
    }

    @Test
    public void skipsMalformedOrNonHttpIncomingUrls() throws Exception {
        File source = temporaryFolder.newFile("Bookmarks-invalid-urls");
        File destination = temporaryFolder.newFile("Bookmarks-invalid-urls.updated");
        Files.write(source.toPath(), fixture().toString(2).getBytes(StandardCharsets.UTF_8));

        ChromiumBookmarksFile.MergeResult result = ChromiumBookmarksFile.merge(
                source,
                destination,
                Arrays.asList(
                        bookmark("JavaScript", "javascript:alert(1)", null),
                        bookmark("Whitespace", "https://example.com/a b", null),
                        bookmark("Valid", "https://valid.example/path", null)));

        assertEquals(1, result.getImported());
        assertEquals(2, result.getSkipped());
        ChromiumBookmarksFile.validate(destination);
    }

    private static Bookmark bookmark(String title, String url, String folder) {
        Bookmark bookmark = new Bookmark();
        bookmark.setTitle(title);
        bookmark.setUrl(url);
        bookmark.setFolder(folder);
        return bookmark;
    }

    private static JSONObject fixture() throws Exception {
        JSONObject roots = new JSONObject();
        roots.put("bookmark_bar", folderNode("1", "Bookmarks bar", new JSONArray()));
        roots.put("other", folderNode("2", "Other bookmarks", new JSONArray()));

        JSONObject existing = new JSONObject()
                .put("date_added", "13300000000000000")
                .put("date_last_used", "0")
                .put("guid", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .put("id", "9")
                .put("name", "Existing")
                .put("type", "url")
                .put("url", "https://existing.example")
                .put("custom_edge_field", "keep-me");
        roots.put("synced", folderNode("3", "Mobile bookmarks", new JSONArray().put(existing)));

        JSONObject fixture = new JSONObject()
                .put("roots", roots)
                .put("sync_metadata", "opaque-sync-metadata")
                .put("unknown_top", new JSONObject().put("keep", true))
                .put("version", 1);
        ChromiumBookmarksFile.refreshChecksums(fixture);
        return fixture;
    }

    private static JSONObject folderNode(String id, String name, JSONArray children) throws Exception {
        return new JSONObject()
                .put("children", children)
                .put("date_added", "13300000000000000")
                .put("date_last_used", "0")
                .put("date_modified", "13300000000000000")
                .put("guid", "00000000-0000-4000-8000-00000000000" + id)
                .put("id", id)
                .put("name", name)
                .put("type", "folder");
    }

    private static JSONObject read(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static boolean hasFolder(JSONArray children, String name) {
        return findFolder(children, name) != null;
    }

    private static JSONObject findFolder(JSONArray children, String name) {
        for (int index = 0; index < children.length(); index++) {
            JSONObject child = children.optJSONObject(index);
            if (child != null
                    && "folder".equals(child.optString("type"))
                    && name.equals(child.optString("name"))) {
                return child;
            }
        }
        return null;
    }
}
