package pro.kisscat.www.bookmarkhelper.sync.tabs;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Bounded parser for Chromium Android's regular tab-model metadata (versions 4 and 5). */
public final class ChromiumTabMetadataReader {
    private static final int MAX_TABS = 10_000;

    private ChromiumTabMetadataReader() {}

    public static List<Entry> read(InputStream source) throws IOException {
        DataInputStream input = new DataInputStream(source);
        int version = input.readInt();
        if (version != 4 && version != 5) {
            throw new IOException("不支持的 Edge 标签页元数据版本：" + version);
        }
        int totalCount = input.readInt();
        int incognitoCount = input.readInt();
        input.readInt(); // Incognito active index.
        input.readInt(); // Regular active index.
        if (totalCount < 0 || totalCount > MAX_TABS
                || incognitoCount < 0 || incognitoCount > totalCount) {
            throw new IOException("Edge 标签页数量无效");
        }
        List<Entry> result = new ArrayList<>(Math.max(0, totalCount - incognitoCount));
        for (int index = 0; index < totalCount; index++) {
            int tabId = input.readInt();
            String url = input.readUTF().trim();
            // Chromium serializes private tabs first. They must never cross this boundary.
            if (index >= incognitoCount && isWebUrl(url)) result.add(new Entry(tabId, url));
        }
        return result;
    }

    private static boolean isWebUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    public static final class Entry {
        private final int tabId;
        private final String url;

        public Entry(int tabId, String url) {
            this.tabId = tabId;
            this.url = url;
        }

        public int getTabId() { return tabId; }
        public String getUrl() { return url; }
    }
}
