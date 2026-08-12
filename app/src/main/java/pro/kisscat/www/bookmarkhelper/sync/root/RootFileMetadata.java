package pro.kisscat.www.bookmarkhelper.sync.root;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** Identity and access metadata that Edge expects on its profile files. */
public final class RootFileMetadata {
    static final String OUTPUT_MARKER = "__BOOKMARKHELPER_METADATA__";

    private final long uid;
    private final long gid;
    private final String mode;
    private final String selinuxContext;

    public RootFileMetadata(long uid, long gid, String mode, String selinuxContext) {
        if (uid < 0L || gid < 0L) {
            throw new IllegalArgumentException("UID/GID must be non-negative");
        }
        if (mode == null || !mode.matches("[0-7]{3,4}")) {
            throw new IllegalArgumentException("Invalid Unix mode");
        }
        if (selinuxContext == null
                || !selinuxContext.matches("[A-Za-z0-9_:.,-]+")
                || selinuxContext.indexOf(':') < 0) {
            throw new IllegalArgumentException("Invalid SELinux context");
        }
        this.uid = uid;
        this.gid = gid;
        this.mode = mode;
        this.selinuxContext = selinuxContext;
    }

    static RootFileMetadata fromRootOutput(List<String> lines) throws IOException {
        if (lines != null) {
            for (String line : lines) {
                if (line == null || !line.startsWith(OUTPUT_MARKER)) {
                    continue;
                }
                String[] fields = line.substring(OUTPUT_MARKER.length()).split("\\|", -1);
                if (fields.length != 4) {
                    break;
                }
                try {
                    return new RootFileMetadata(
                            Long.parseLong(fields[0]),
                            Long.parseLong(fields[1]),
                            fields[2],
                            fields[3]);
                } catch (IllegalArgumentException ignored) {
                    break;
                }
            }
        }
        throw new IOException("无法解析 Edge 文件的 UID/GID/mode/SELinux 元数据");
    }

    void put(Properties properties, String prefix) {
        properties.setProperty(prefix + "uid", Long.toString(uid));
        properties.setProperty(prefix + "gid", Long.toString(gid));
        properties.setProperty(prefix + "mode", mode);
        properties.setProperty(prefix + "selinux", selinuxContext);
    }

    static RootFileMetadata get(Properties properties, String prefix) throws IOException {
        String uidValue = properties.getProperty(prefix + "uid");
        String gidValue = properties.getProperty(prefix + "gid");
        String modeValue = properties.getProperty(prefix + "mode");
        String contextValue = properties.getProperty(prefix + "selinux");
        if (uidValue == null || gidValue == null || modeValue == null || contextValue == null) {
            throw new IOException("Root 事务记录缺少文件元数据");
        }
        try {
            return new RootFileMetadata(
                    Long.parseLong(uidValue),
                    Long.parseLong(gidValue),
                    modeValue,
                    contextValue);
        } catch (IllegalArgumentException error) {
            throw new IOException("Root 事务记录中的文件元数据无效", error);
        }
    }

    public long getUid() {
        return uid;
    }

    public long getGid() {
        return gid;
    }

    public String getMode() {
        return mode;
    }

    public String getSelinuxContext() {
        return selinuxContext;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RootFileMetadata)) {
            return false;
        }
        RootFileMetadata metadata = (RootFileMetadata) other;
        return uid == metadata.uid
                && gid == metadata.gid
                && mode.equals(metadata.mode)
                && selinuxContext.equals(metadata.selinuxContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, gid, mode, selinuxContext);
    }

    @Override
    public String toString() {
        return "uid=" + uid + ",gid=" + gid + ",mode=" + mode
                + ",selinux=" + selinuxContext;
    }
}
