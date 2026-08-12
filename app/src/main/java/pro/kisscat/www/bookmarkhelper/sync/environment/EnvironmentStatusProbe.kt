package pro.kisscat.www.bookmarkhelper.sync.environment

import pro.kisscat.www.bookmarkhelper.sync.root.RootShell

enum class ActivationState {
    CHECKING,
    ACTIVE,
    INSTALLED,
    INACTIVE,
    UNKNOWN,
}

data class RuntimeEnvironmentStatus(
    val root: ActivationState,
    val lsposed: ActivationState,
    val rootManager: String,
)

/** Fast, read-only environment probe used by the two home status cards. */
object EnvironmentStatusProbe {
    fun read(): RuntimeEnvironmentStatus {
        val root = readRoot()
        return RuntimeEnvironmentStatus(root.first, readLsposed(root.first), root.second)
    }

    fun readRoot(): Pair<ActivationState, String> {
        if (!RootShell.isAvailable()) return ActivationState.INACTIVE to "未检测到"
        val result = RootShell.run(
            8L,
            "if command -v ksud >/dev/null 2>&1 || [ -d /data/adb/ksu ]; then echo KernelSU; " +
                "elif command -v apd >/dev/null 2>&1 || [ -d /data/adb/ap ]; then echo APatch; " +
                "elif command -v magisk >/dev/null 2>&1 || [ -d /data/adb/magisk ]; then echo Magisk; " +
                "else echo Root; fi",
        )
        val manager = result.stdoutLines.firstOrNull()?.trim().orEmpty().ifBlank { "Root" }
        return ActivationState.ACTIVE to manager
    }

    fun readLsposed(rootState: ActivationState = ActivationState.ACTIVE): ActivationState {
        if (rootState != ActivationState.ACTIVE) return ActivationState.UNKNOWN
        val result = RootShell.run(
            8L,
            "if pidof lspd >/dev/null 2>&1 || ps -A 2>/dev/null | grep -q '[l]spd'; then " +
                "echo active; elif [ -d /data/adb/lspd ] || " +
                "[ -d /data/adb/modules/zygisk_lsposed ]; then echo installed; else echo inactive; fi",
        )
        return when {
            !result.isSuccess -> ActivationState.UNKNOWN
            result.stdoutLines.any { it.trim() == "active" } -> ActivationState.ACTIVE
            result.stdoutLines.any { it.trim() == "installed" } -> ActivationState.INSTALLED
            else -> ActivationState.INACTIVE
        }
    }
}
