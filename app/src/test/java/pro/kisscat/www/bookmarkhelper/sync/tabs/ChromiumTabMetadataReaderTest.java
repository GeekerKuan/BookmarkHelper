package pro.kisscat.www.bookmarkhelper.sync.tabs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import org.junit.Test;
import org.junit.Assume;

public class ChromiumTabMetadataReaderTest {
    @Test
    public void readsRegularTabsAndExcludesIncognitoAndInternalUrls() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(5);
            output.writeInt(4);
            output.writeInt(1);
            output.writeInt(0);
            output.writeInt(0);
            write(output, 1, "https://private.example/");
            write(output, 2, "https://one.example/path");
            write(output, 3, "edge://newtab/");
            write(output, 4, "http://two.example/");
        }
        List<ChromiumTabMetadataReader.Entry> result = ChromiumTabMetadataReader.read(
                new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(2, result.size());
        assertEquals("https://one.example/path", result.get(0).getUrl());
        assertEquals("http://two.example/", result.get(1).getUrl());
    }

    @Test(expected = IOException.class)
    public void rejectsUnboundedCount() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(5);
            output.writeInt(10_001);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
        }
        ChromiumTabMetadataReader.read(new ByteArrayInputStream(bytes.toByteArray()));
    }

    /** Optional local fixture check; the path is supplied only by the tester and is never logged. */
    @Test
    public void parsesProvidedRealWorldFixtureWithoutExposingContent() throws Exception {
        String path = System.getenv("EDGE_TAB_SAMPLE");
        Assume.assumeTrue(path != null && new File(path).isFile());
        try (FileInputStream input = new FileInputStream(path)) {
            assertFalse(ChromiumTabMetadataReader.read(input).isEmpty());
        }
    }

    private static void write(DataOutputStream output, int id, String url) throws IOException {
        output.writeInt(id);
        output.writeUTF(url);
    }
}
