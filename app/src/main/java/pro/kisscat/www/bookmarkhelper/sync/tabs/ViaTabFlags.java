package pro.kisscat.www.bookmarkhelper.sync.tabs;

/** Via 6.x tab-state bits verified against real browser data samples. */
final class ViaTabFlags {
    private static final int OPEN = 1 << 1;
    private ViaTabFlags() {}

    static boolean isOpen(int flags) {
        return (flags & OPEN) != 0;
    }
}
