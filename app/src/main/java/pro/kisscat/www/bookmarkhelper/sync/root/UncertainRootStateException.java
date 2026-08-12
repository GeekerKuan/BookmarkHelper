package pro.kisscat.www.bookmarkhelper.sync.root;

import java.io.IOException;

/**
 * A mutation may still be running or its target could not be verified.
 * Callers must persist this state and must not launch the target application.
 */
public final class UncertainRootStateException extends IOException {
    private final boolean rebootRequired;
    private final RootFileMetadata expectedMetadata;

    public UncertainRootStateException(
            String message,
            boolean rebootRequired,
            RootFileMetadata expectedMetadata) {
        super(message);
        this.rebootRequired = rebootRequired;
        this.expectedMetadata = expectedMetadata;
    }

    public UncertainRootStateException(
            String message,
            boolean rebootRequired,
            RootFileMetadata expectedMetadata,
            Throwable cause) {
        super(message, cause);
        this.rebootRequired = rebootRequired;
        this.expectedMetadata = expectedMetadata;
    }

    public boolean isRebootRequired() {
        return rebootRequired;
    }

    public RootFileMetadata getExpectedMetadata() {
        return expectedMetadata;
    }
}
