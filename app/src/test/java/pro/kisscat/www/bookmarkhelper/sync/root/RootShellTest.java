package pro.kisscat.www.bookmarkhelper.sync.root;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RootShellTest {
    @Test
    public void quotesSpacesAndSingleQuotesWithoutLosingData() {
        assertEquals("'plain path'", RootShell.quote("plain path"));
        assertEquals("'Edge'\\''s Bookmarks'", RootShell.quote("Edge's Bookmarks"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesNullShellValues() {
        RootShell.quote(null);
    }
}
