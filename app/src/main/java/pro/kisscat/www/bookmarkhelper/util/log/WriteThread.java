package pro.kisscat.www.bookmarkhelper.util.log;

import java.util.concurrent.atomic.AtomicBoolean;

/** Single-flight logger worker with a final queue recheck to close enqueue races. */
final class WriteThread extends Thread {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private WriteThread() {
        super("bookmark-helper-log-writer");
        setDaemon(true);
    }

    static void schedule() {
        if (RUNNING.compareAndSet(false, true)) {
            new WriteThread().start();
        }
    }

    @Override
    public void run() {
        try {
            do {
                LogHelper.flush();
            } while (LogHelper.hasPendingEntries());
        } finally {
            RUNNING.set(false);
            // An entry may have arrived between the final empty check and the
            // flag reset. Claim a fresh worker rather than leaving it stranded.
            if (LogHelper.hasPendingEntries()) {
                schedule();
            }
        }
    }
}
