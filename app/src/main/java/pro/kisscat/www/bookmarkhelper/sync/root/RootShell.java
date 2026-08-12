package pro.kisscat.www.bookmarkhelper.sync.root;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Small, timeout-bound wrapper around the device's root shell.
 *
 * <p>Commands are supplied only by BookmarkHelper. Callers must shell-quote
 * every path or value that is not a fixed literal.</p>
 */
public final class RootShell {
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private RootShell() {
    }

    public static boolean isAvailable() {
        Result result = run(10L, "id -u");
        if (!result.isSuccess()) {
            return false;
        }
        for (String line : result.getStdoutLines()) {
            if ("0".equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    public static Result run(String... commands) {
        return run(DEFAULT_TIMEOUT_SECONDS, commands);
    }

    public static Result run(long timeoutSeconds, String... commands) {
        Process process = null;
        BufferedWriter stdin = null;
        StreamCollector stdout = null;
        StreamCollector stderr = null;
        boolean timedOut = false;
        int exitCode = -1;
        try {
            process = Runtime.getRuntime().exec("su");
            stdout = new StreamCollector(process.getInputStream());
            stderr = new StreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();

            stdin = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            if (commands != null) {
                for (String command : commands) {
                    if (command == null || command.trim().isEmpty()) {
                        continue;
                    }
                    stdin.write(command);
                    stdin.newLine();
                }
            }
            stdin.write("exit");
            stdin.newLine();
            stdin.flush();
            stdin.close();
            stdin = null;

            if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                exitCode = process.exitValue();
            } else {
                timedOut = true;
                process.destroy();
                if (!process.waitFor(1L, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception error) {
            // Once su has started, an I/O failure or thread interruption cannot
            // prove that the root-side shell (or one of its children) stopped.
            // Reuse the timedOut/unknown bit so every mutating caller fails closed.
            boolean commandMayStillRun = process != null;
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            List<String> lines = new ArrayList<>();
            lines.add(error.getClass().getSimpleName() + ": " + error.getMessage());
            return new Result(
                    -1,
                    commandMayStillRun,
                    Collections.<String>emptyList(),
                    lines);
        } finally {
            if (stdin != null) {
                try {
                    stdin.close();
                } catch (IOException ignored) {
                    // Nothing else can be recovered here.
                }
            }
            join(stdout);
            join(stderr);
            if (process != null) {
                process.destroy();
            }
        }
        return new Result(
                exitCode,
                timedOut,
                stdout == null ? Collections.<String>emptyList() : stdout.snapshot(),
                stderr == null ? Collections.<String>emptyList() : stderr.snapshot());
    }

    public static String quote(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Shell value must not be null");
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void join(StreamCollector collector) {
        if (collector == null) {
            return;
        }
        try {
            collector.join(1500L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class StreamCollector extends Thread {
        private final InputStream stream;
        private final List<String> lines = Collections.synchronizedList(new ArrayList<String>());

        private StreamCollector(InputStream stream) {
            super("bookmark-helper-root-stream");
            this.stream = stream;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException error) {
                lines.add(error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }

        private List<String> snapshot() {
            synchronized (lines) {
                return new ArrayList<>(lines);
            }
        }
    }

    public static final class Result {
        private final int exitCode;
        private final boolean timedOut;
        private final List<String> stdoutLines;
        private final List<String> stderrLines;

        private Result(int exitCode, boolean timedOut, List<String> stdout, List<String> stderr) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.stdoutLines = stdout;
            this.stderrLines = stderr;
        }

        public boolean isSuccess() {
            return !timedOut && exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isTimedOut() {
            return timedOut;
        }

        public List<String> getStdoutLines() {
            return new ArrayList<>(stdoutLines);
        }

        public String getStdout() {
            return joinLines(stdoutLines);
        }

        public String getStderr() {
            return joinLines(stderrLines);
        }

        public String describeFailure() {
            if (timedOut) {
                return "Root command timed out";
            }
            String stderr = getStderr();
            return stderr.isEmpty() ? "Root command failed (exit " + exitCode + ")" : stderr;
        }

        private static String joinLines(List<String> lines) {
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(line);
            }
            return result.toString();
        }
    }
}
