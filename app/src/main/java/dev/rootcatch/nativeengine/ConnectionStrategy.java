package dev.rootcatch.nativeengine;

public enum ConnectionStrategy {
    ATTACH("librootcatch-inject.so", "17.22.1",
            "c5a833cd6d96fcb0eda71b338e19cdcf8ef0ac653c5887c1d40c76081ed789cc"),
    EARLY_SPAWN("libsilph-spawn.so", "17.23.3",
            "2c2049abe6cb5538d60bbad7477734caf7b2ab69002b9701ece0e5b48246ed59"),
    DIRECT_ATTACH("libsilph-spawn.so", "17.23.3",
            "2c2049abe6cb5538d60bbad7477734caf7b2ab69002b9701ece0e5b48246ed59"),
    COMPAT_ATTACH("librootcatch-inject.so", "17.22.1",
            "c5a833cd6d96fcb0eda71b338e19cdcf8ef0ac653c5887c1d40c76081ed789cc");

    public final String library, version, sha256;
    public static final String LOADER_LIBRARY = "libsilph-loader.so";
    public static final String LOADER_SHA256 = "c90ac879cfca797e726d7bc5c4599df3b4f5ab8202363994ebeeecb943fbf397";

    ConnectionStrategy(String library, String version, String sha256) {
        this.library = library; this.version = version; this.sha256 = sha256;
    }

    public static ConnectionStrategy forSdk(int sdk) {
        if (sdk < 29) throw new IllegalArgumentException("Android 10 or newer required");
        return sdk == 29 ? ATTACH : EARLY_SPAWN;
    }

    public boolean earlySpawn() { return this == EARLY_SPAWN; }
    public boolean guarded() { return this != ATTACH; }

    public String spawnCommand(String binary, String guard, String agent, String directory, String json) {
        if (!earlySpawn()) throw new IllegalStateException("Spawn backend not selected");
        return guardedCommand(binary, guard, agent, directory, json, "-f " + EnginePolicy.quote(EnginePolicy.GAME));
    }
    public String directCommand(String binary, String guard, String agent, String directory, int pid, String json) {
        if (earlySpawn() || !guarded() || pid <= 1) throw new IllegalArgumentException("Invalid direct connection");
        return guardedCommand(binary, guard, agent, directory, json, "-p " + pid);
    }
    public static String probeCommand(String guard) {
        runtimePath(guard);
        return "( parent_mnt_ns=$(readlink /proc/self/ns/mnt) || exit 90; "
                + "exec unshare -m -- /system/bin/sh " + EnginePolicy.quote(guard)
                + " --probe \"$parent_mnt_ns\" )";
    }
    private static void runtimePath(String path) {
        if (path == null || !path.startsWith("/data/") || path.contains("/../") || path.endsWith("/.."))
            throw new IllegalArgumentException("Invalid runtime path");
        EnginePolicy.quote(path);
    }
    private String guardedCommand(String binary, String guard, String agent, String directory, String json, String target) {
        for (String path : new String[]{binary, guard, agent, directory}) {
            runtimePath(path);
        }
        return "parent_mnt_ns=$(readlink /proc/self/ns/mnt) || exit 90; "
                + "exec unshare -m -- /system/bin/sh " + EnginePolicy.quote(guard)
                + " \"$parent_mnt_ns\" " + EnginePolicy.quote(binary)
                + " " + EnginePolicy.quote(directory)
                + " " + EnginePolicy.quote(binary.substring(0, binary.lastIndexOf('/') + 1) + LOADER_LIBRARY)
                + " " + target
                + " -s " + EnginePolicy.quote(agent) + " -R qjs -i -P " + EnginePolicy.quote(json);
    }
}
