package pro.kisscat.www.bookmarkhelper.sync.root;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class RootFileMetadataTest {
    @Test
    public void parsesTaggedRootOutputWithoutTrustingOtherStdout() throws Exception {
        RootFileMetadata metadata = RootFileMetadata.fromRootOutput(Arrays.asList(
                "root manager banner",
                RootFileMetadata.OUTPUT_MARKER
                        + "10234|10234|600|u:object_r:app_data_file:s0:c1,c2"));

        assertEquals(10234L, metadata.getUid());
        assertEquals(10234L, metadata.getGid());
        assertEquals("600", metadata.getMode());
        assertEquals("u:object_r:app_data_file:s0:c1,c2", metadata.getSelinuxContext());
    }

    @Test
    public void propertiesRoundTripPreservesSecurityIdentity() throws Exception {
        RootFileMetadata original = new RootFileMetadata(
                10123L,
                10123L,
                "660",
                "u:object_r:app_data_file:s0:c10,c20");
        Properties properties = new Properties();
        original.put(properties, "metadata.");

        assertEquals(original, RootFileMetadata.get(properties, "metadata."));
    }

    @Test(expected = IOException.class)
    public void rejectsUntaggedOrIncompleteOutput() throws Exception {
        RootFileMetadata.fromRootOutput(Arrays.asList(
                "10123|10123|600|u:object_r:app_data_file:s0",
                RootFileMetadata.OUTPUT_MARKER + "10123|10123|600"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingSelinuxDomain() {
        new RootFileMetadata(10123L, 10123L, "600", "unknown");
    }
}
