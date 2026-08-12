package pro.kisscat.www.bookmarkhelper.sync.history;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class HistorySyncServiceTest {
    @Test
    public void convertsUnixSecondsToChromiumMicroseconds() throws IOException {
        assertEquals(
                11_644_473_600_000_000L,
                HistorySyncService.toChromiumMicrosForTest(0L));
        assertEquals(
                13_430_752_987_000_000L,
                HistorySyncService.toChromiumMicrosForTest(1_786_279_387L));
    }

    @Test(expected = IOException.class)
    public void rejectsNegativeTimestamp() throws IOException {
        HistorySyncService.toChromiumMicrosForTest(-1L);
    }

    @Test
    public void convertsChromiumMicrosecondsToUnixMilliseconds() throws IOException {
        assertEquals(0L, HistorySyncService.fromChromiumMicrosForTest(
                11_644_473_600_000_000L));
        assertEquals(1_786_279_387_000L, HistorySyncService.fromChromiumMicrosForTest(
                13_430_752_987_000_000L));
    }

    @Test(expected = IOException.class)
    public void rejectsPreUnixChromiumTimestamp() throws IOException {
        HistorySyncService.fromChromiumMicrosForTest(11_644_473_599_999_999L);
    }

    @Test
    public void visibleTransitionContainsChainStartAndEnd() {
        int transition = HistorySyncService.visibleLinkTransitionForTest();
        assertEquals(0x30000000, transition & 0x30000000);
    }

    @Test
    public void recognizesOnlyBookmarkHelperLegacyInvisibleVisit() {
        assertEquals(true, HistorySyncService.isLegacyInvisibleVisitForTest(
                0, 0, 0, true, 0));
        assertEquals(false, HistorySyncService.isLegacyInvisibleVisitForTest(
                0x30000000, 1, 1, true, 0));
        assertEquals(false, HistorySyncService.isLegacyInvisibleVisitForTest(
                0, 0, 0, false, -1));
        assertEquals(false, HistorySyncService.isLegacyInvisibleVisitForTest(
                0, 0, 0, true, 1));
    }
}
