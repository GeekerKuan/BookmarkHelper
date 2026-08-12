package pro.kisscat.www.bookmarkhelper.sync.chromium;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import pro.kisscat.www.bookmarkhelper.entry.app.Bookmark;

/** Preserving editor for Chromium's profile-level {@code Bookmarks} JSON file. */
public final class ChromiumBookmarksFile {
    private static final long WINDOWS_EPOCH_DELTA_MICROSECONDS = 11644473600000000L;
    private static final long MAX_BOOKMARKS_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_INCOMING_BOOKMARKS = 50_000;
    private static final int MAX_NODES = 200_000;
    private static final int MAX_TITLE_LENGTH = 4_096;
    private static final int MAX_URL_LENGTH = 65_536;
    private static final int MAX_FOLDER_PATH_LENGTH = 4_096;
    private static final int MAX_FOLDER_SEGMENT_LENGTH = 255;
    private static final String IMPORT_FOLDER_NAME = "Via Import";
    private static final String[] CHECKSUM_ROOT_ORDER = {"bookmark_bar", "other", "synced"};
    private static final Pattern GUID_PATTERN = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private ChromiumBookmarksFile() {
    }

    public static MergeResult merge(File source, File destination, List<Bookmark> incoming)
            throws IOException {
        if (incoming == null) {
            throw new IOException("Via 书签列表为空");
        }
        if (incoming.size() > MAX_INCOMING_BOOKMARKS) {
            throw new IOException("Via 书签数量异常过多，已停止写入");
        }
        try {
            JSONObject document = readAndValidate(source);
            JSONObject roots = document.getJSONObject("roots");

            ScanState state = scan(roots);
            JSONObject targetRoot = chooseMobileRoot(roots);
            JSONArray rootChildren = ensureChildren(targetRoot);
            JSONObject importFolder = findFolder(rootChildren, IMPORT_FOLDER_NAME);

            int imported = 0;
            int skipped = 0;
            for (Bookmark bookmark : incoming) {
                if (bookmark == null || !isValidIncomingUrl(bookmark.getUrl())
                        || !isValidIncomingTitle(bookmark.getTitle())
                        || !isValidFolderPath(bookmark.getFolder())) {
                    skipped++;
                    continue;
                }
                String url = bookmark.getUrl().trim();
                if (state.urls.contains(url)) {
                    skipped++;
                    continue;
                }
                if (importFolder == null) {
                    importFolder = newFolder(IMPORT_FOLDER_NAME, state);
                    rootChildren.put(importFolder);
                }
                JSONArray destinationChildren = folderChildren(importFolder, bookmark.getFolder(), state);
                String title = isBlank(bookmark.getTitle()) ? url : bookmark.getTitle().trim();
                destinationChildren.put(newUrl(title, url, state));
                state.urls.add(url);
                imported++;
            }

            if (imported > 0) {
                String now = chromiumTimeNow();
                importFolder.put("date_modified", now);
                targetRoot.put("date_modified", now);
                refreshChecksums(document);
            }

            writeUtf8(destination, document.toString(2));
            if (destination.length() > MAX_BOOKMARKS_BYTES) {
                if (!destination.delete()) {
                    destination.deleteOnExit();
                }
                throw new IOException("生成的 Edge Bookmarks 文件异常过大，已停止写入");
            }
            // Run the same complete parser, structure checks and checksum checks
            // that are used for an existing Edge file before root ever sees it.
            readAndValidate(destination);
            return new MergeResult(imported, skipped, state.urls.size());
        } catch (JSONException error) {
            throw new IOException("解析或生成 Edge Bookmarks JSON 失败", error);
        }
    }

    public static void validate(File source) throws IOException {
        readAndValidate(source);
    }

    private static JSONObject readAndValidate(File source) throws IOException {
        if (source == null || !source.isFile() || source.length() == 0L) {
            throw new IOException("Edge Bookmarks 文件不存在或为空");
        }
        if (source.length() > MAX_BOOKMARKS_BYTES) {
            throw new IOException("Edge Bookmarks 文件异常过大，已停止写入");
        }
        try {
            JSONObject document = new JSONObject(readUtf8(source));
            if (document.optInt("version", -1) != 1) {
                throw new IOException("不支持的 Edge Bookmarks 文件版本");
            }
            JSONObject roots = document.optJSONObject("roots");
            if (roots == null) {
                throw new IOException("Edge Bookmarks 缺少 roots 节点");
            }
            scan(roots);
            chooseMobileRoot(roots);
            verifyExistingChecksums(document, roots);
            return document;
        } catch (JSONException error) {
            throw new IOException("解析 Edge Bookmarks JSON 失败", error);
        }
    }

    private static ScanState scan(JSONObject roots) throws JSONException, IOException {
        ScanState state = new ScanState();
        boolean foundRoot = false;
        for (String rootName : CHECKSUM_ROOT_ORDER) {
            JSONObject root = roots.optJSONObject(rootName);
            if (root != null) {
                foundRoot = true;
                scanNode(root, state);
            }
        }
        if (!foundRoot) {
            throw new IOException("Edge Bookmarks 没有可识别的根节点");
        }
        return state;
    }

    private static void scanNode(JSONObject node, ScanState state)
            throws JSONException, IOException {
        state.nodeCount++;
        if (state.nodeCount > MAX_NODES) {
            throw new IOException("Edge 书签节点数量异常过多，已停止写入");
        }

        String id = node.optString("id", "");
        if (!id.matches("[1-9][0-9]*")) {
            throw new IOException("Edge 书签包含缺失或非法 ID，已停止写入");
        }
        long numericId;
        try {
            numericId = Long.parseLong(id);
        } catch (NumberFormatException error) {
            throw new IOException("Edge 书签 ID 超出安全范围，已停止写入", error);
        }
        if (!state.ids.add(numericId)) {
            throw new IOException("Edge 书签包含重复 ID，已停止写入");
        }
        state.maximumId = Math.max(state.maximumId, numericId);

        String guid = node.optString("guid", "");
        String normalizedGuid = guid.toLowerCase(Locale.US);
        if (!GUID_PATTERN.matcher(normalizedGuid).matches()) {
            throw new IOException("Edge 书签包含缺失或非法 GUID，已停止写入");
        }
        if (!state.guids.add(normalizedGuid)) {
            throw new IOException("Edge 书签包含重复 GUID，已停止写入");
        }

        Object nameValue = node.opt("name");
        if (!(nameValue instanceof String)
                || ((String) nameValue).length() > MAX_TITLE_LENGTH) {
            throw new IOException("Edge 书签包含非法或过长标题，已停止写入");
        }

        String type = node.optString("type", "");
        if ("url".equals(type)) {
            Object urlValue = node.opt("url");
            if (!(urlValue instanceof String)
                    || ((String) urlValue).isEmpty()
                    || ((String) urlValue).length() > MAX_URL_LENGTH) {
                throw new IOException("Edge 书签包含非法或过长网址，已停止写入");
            }
            state.urls.add((String) urlValue);
            return;
        }
        if (!"folder".equals(type)) {
            throw new IOException("Edge 书签包含未知节点类型，已停止写入");
        }
        JSONArray children = node.optJSONArray("children");
        if (children == null) {
            throw new IOException("Edge 收藏夹节点缺少 children，已停止写入");
        }
        for (int index = 0; index < children.length(); index++) {
            JSONObject child = children.optJSONObject(index);
            if (child != null) {
                scanNode(child, state);
            }
        }
    }

    private static JSONObject chooseMobileRoot(JSONObject roots) throws IOException {
        JSONObject root = roots.optJSONObject("synced");
        if (root == null) {
            root = roots.optJSONObject("other");
        }
        if (root == null) {
            root = roots.optJSONObject("bookmark_bar");
        }
        if (root == null) {
            throw new IOException("Edge Bookmarks 中找不到可写入的收藏夹根节点");
        }
        if (!"folder".equals(root.optString("type"))
                || root.optJSONArray("children") == null) {
            throw new IOException("Edge 收藏夹根节点结构异常，已停止写入");
        }
        return root;
    }

    private static void verifyExistingChecksums(JSONObject document, JSONObject roots)
            throws IOException, JSONException {
        Checksums computed = computeChecksums(roots);
        String md5 = document.optString("checksum", "");
        if (!md5.matches("(?i)[0-9a-f]{32}") || !md5.equalsIgnoreCase(computed.md5)) {
            throw new IOException("Edge Bookmarks 的 MD5 校验不一致，请先正常打开并关闭 Edge 后重试");
        }
        if (document.has("checksum_sha256")) {
            String sha256 = document.optString("checksum_sha256", "");
            if (!sha256.matches("(?i)[0-9a-f]{64}")
                    || !sha256.equalsIgnoreCase(computed.sha256)) {
                throw new IOException("Edge Bookmarks 的 SHA-256 校验不一致，已停止写入");
            }
        }
    }

    static void refreshChecksums(JSONObject document) throws IOException, JSONException {
        JSONObject roots = document.optJSONObject("roots");
        if (roots == null) {
            throw new IOException("Edge Bookmarks 缺少 roots 节点");
        }
        Checksums checksums = computeChecksums(roots);
        document.put("checksum", checksums.md5);
        // Preserve the profile's codec generation. Edge 150 currently writes
        // only MD5 while newer Chromium builds may already carry SHA-256.
        if (document.has("checksum_sha256")) {
            document.put("checksum_sha256", checksums.sha256);
        }
    }

    private static JSONArray folderChildren(JSONObject importFolder, String folderPath, ScanState state)
            throws JSONException, IOException {
        String modifiedAt = chromiumTimeNow();
        importFolder.put("date_modified", modifiedAt);
        JSONArray children = ensureChildren(importFolder);
        if (isBlank(folderPath)) {
            return children;
        }
        String[] segments = folderPath.replace('\\', '/').split("/+");
        for (String raw : segments) {
            String name = raw.trim();
            if (name.isEmpty() || isPermanentRootLabel(name) || IMPORT_FOLDER_NAME.equals(name)) {
                continue;
            }
            JSONObject folder = findFolder(children, name);
            if (folder == null) {
                folder = newFolder(name, state);
                children.put(folder);
            }
            folder.put("date_modified", modifiedAt);
            children = ensureChildren(folder);
        }
        return children;
    }

    private static boolean isPermanentRootLabel(String value) {
        String normalized = value.toLowerCase(Locale.US).replace(" ", "_");
        return "bookmark_bar".equals(normalized)
                || "bookmarks_bar".equals(normalized)
                || "other".equals(normalized)
                || "other_bookmarks".equals(normalized)
                || "synced".equals(normalized)
                || "mobile_bookmarks".equals(normalized);
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

    private static JSONObject newFolder(String name, ScanState state)
            throws JSONException, IOException {
        String now = chromiumTimeNow();
        JSONObject result = new JSONObject();
        result.put("children", new JSONArray());
        result.put("date_added", now);
        result.put("date_last_used", "0");
        result.put("date_modified", now);
        result.put("guid", nextGuid(state));
        result.put("id", nextId(state));
        result.put("name", name);
        result.put("source", "unknown");
        result.put("type", "folder");
        return result;
    }

    private static JSONObject newUrl(String title, String url, ScanState state)
            throws JSONException, IOException {
        JSONObject result = new JSONObject();
        result.put("date_added", chromiumTimeNow());
        result.put("date_last_used", "0");
        result.put("guid", nextGuid(state));
        result.put("id", nextId(state));
        result.put("name", title);
        result.put("show_icon", false);
        result.put("source", "unknown");
        result.put("type", "url");
        result.put("url", url);
        result.put("visit_count", 0);
        return result;
    }

    private static String nextGuid(ScanState state) {
        String guid;
        do {
            guid = UUID.randomUUID().toString().toLowerCase(Locale.US);
        } while (!state.guids.add(guid));
        return guid;
    }

    private static String nextId(ScanState state) throws IOException {
        if (state.maximumId == Long.MAX_VALUE) {
            throw new IOException("Edge 书签 ID 已达到上限，无法安全追加");
        }
        long id = ++state.maximumId;
        if (!state.ids.add(id)) {
            throw new IOException("无法生成唯一的 Edge 书签 ID");
        }
        state.nodeCount++;
        if (state.nodeCount > MAX_NODES) {
            throw new IOException("导入后的 Edge 书签节点数量过多，已停止写入");
        }
        return Long.toString(id);
    }

    private static JSONArray ensureChildren(JSONObject folder) throws JSONException {
        JSONArray children = folder.optJSONArray("children");
        if (children == null) {
            children = new JSONArray();
            folder.put("children", children);
        }
        return children;
    }

    private static Checksums computeChecksums(JSONObject roots) throws IOException, JSONException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            for (String rootName : CHECKSUM_ROOT_ORDER) {
                JSONObject root = roots.optJSONObject(rootName);
                if (root != null) {
                    updateChecksum(root, md5, sha256);
                }
            }
            return new Checksums(hex(md5.digest()), hex(sha256.digest()));
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("设备缺少书签校验算法", error);
        }
    }

    private static void updateChecksum(JSONObject node, MessageDigest md5, MessageDigest sha256)
            throws JSONException {
        String id = node.optString("id", "");
        String title = node.optString("name", "");
        String type = node.optString("type", "");
        updateUtf8(id, md5, sha256);
        updateUtf16Le(title, md5, sha256);
        updateUtf8(type, md5, sha256);
        if ("url".equals(type)) {
            updateUtf8(node.optString("url", ""), md5, sha256);
            return;
        }
        JSONArray children = node.optJSONArray("children");
        if (children == null) {
            return;
        }
        for (int index = 0; index < children.length(); index++) {
            JSONObject child = children.optJSONObject(index);
            if (child != null) {
                updateChecksum(child, md5, sha256);
            }
        }
    }

    private static void updateUtf8(String value, MessageDigest... digests) {
        update(value.getBytes(StandardCharsets.UTF_8), digests);
    }

    private static void updateUtf16Le(String value, MessageDigest... digests) {
        update(value.getBytes(StandardCharsets.UTF_16LE), digests);
    }

    private static void update(byte[] value, MessageDigest... digests) {
        for (MessageDigest digest : digests) {
            digest.update(value);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String chromiumTimeNow() {
        long microseconds = System.currentTimeMillis() * 1000L
                + WINDOWS_EPOCH_DELTA_MICROSECONDS;
        return Long.toString(microseconds);
    }

    private static String readUtf8(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeUtf8(File file, String value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建 Edge 工作目录");
        }
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(value);
            writer.write('\n');
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isValidIncomingTitle(String title) {
        return title == null || title.trim().length() <= MAX_TITLE_LENGTH;
    }

    private static boolean isValidFolderPath(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return true;
        }
        if (folderPath.length() > MAX_FOLDER_PATH_LENGTH) {
            return false;
        }
        String[] segments = folderPath.replace('\\', '/').split("/+", -1);
        for (String segment : segments) {
            if (segment.trim().length() > MAX_FOLDER_SEGMENT_LENGTH) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIncomingUrl(String value) {
        if (isBlank(value)) {
            return false;
        }
        String url = value.trim();
        if (url.length() > MAX_URL_LENGTH) {
            return false;
        }
        for (int index = 0; index < url.length(); index++) {
            char character = url.charAt(index);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                return false;
            }
        }
        try {
            URI parsed = new URI(url);
            String scheme = parsed.getScheme();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && !isBlank(parsed.getRawAuthority());
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static final class ScanState {
        private long maximumId;
        private int nodeCount;
        private final Set<String> urls = new HashSet<>();
        private final Set<String> guids = new HashSet<>();
        private final Set<Long> ids = new HashSet<>();
    }

    private static final class Checksums {
        private final String md5;
        private final String sha256;

        private Checksums(String md5, String sha256) {
            this.md5 = md5;
            this.sha256 = sha256;
        }
    }

    public static final class MergeResult {
        private final int imported;
        private final int skipped;
        private final int totalUniqueUrls;

        private MergeResult(int imported, int skipped, int totalUniqueUrls) {
            this.imported = imported;
            this.skipped = skipped;
            this.totalUniqueUrls = totalUniqueUrls;
        }

        public int getImported() {
            return imported;
        }

        public int getSkipped() {
            return skipped;
        }

        public int getTotalUniqueUrls() {
            return totalUniqueUrls;
        }
    }
}
