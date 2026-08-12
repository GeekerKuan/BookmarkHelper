package pro.kisscat.www.bookmarkhelper.sync.tabs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ViaTabFlagsTest {
    @Test
    public void closedSavedTabIsNotOpen() {
        assertFalse(ViaTabFlags.isOpen(0));
    }

    @Test
    public void regularOpenTabCarriesOpenBit() {
        assertTrue(ViaTabFlags.isOpen(2));
    }

    @Test
    public void foregroundOpenTabStillCarriesOpenBit() {
        assertTrue(ViaTabFlags.isOpen(6));
    }
}
