package dev.rootcatch.nativeengine;

public enum ConnectionStrategy {
    ATTACH("librootcatch-inject.so", "17.22.1",
            "2e8121da766dac95495742b947eabbade30355831e8338c10d963f5ebaa980ca"),
    EARLY_SPAWN("libsilph-spawn.so", "17.23.3",
            "ae80b4da9717fecc7c86ed2e9dd072d397a5859df8be26020af9d01a10d80426"),
    DIRECT_ATTACH("libsilph-spawn.so", "17.23.3",
            "ae80b4da9717fecc7c86ed2e9dd072d397a5859df8be26020af9d01a10d80426"),
    COMPAT_ATTACH("librootcatch-inject.so", "17.22.1",
            "2e8121da766dac95495742b947eabbade30355831e8338c10d963f5ebaa980ca");

    public final String library, version, sha256;
    public static final String LOADER_LIBRARY = "libsilph-loader.so";
    public static final String LOADER_SHA256 = "44eb05c669e4deafa42381a11d945072c60b8704919ba643ae5533c6e8c62266";

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
        return "( mnt_scope_ref=$(readlink /proc/self/ns/mnt) || exit 90; "
                + "exec unshare -m -- /system/bin/sh " + EnginePolicy.quote(guard)
                + " --probe \"$mnt_scope_ref\" )";
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
        return "mnt_scope_ref=$(readlink /proc/self/ns/mnt) || exit 90; "
                + "exec unshare -m -- /system/bin/sh " + EnginePolicy.quote(guard)
                + " \"$mnt_scope_ref\" " + EnginePolicy.quote(binary)
                + " " + EnginePolicy.quote(directory)
                + " " + EnginePolicy.quote(binary.substring(0, binary.lastIndexOf('/') + 1) + LOADER_LIBRARY)
                + " " + target
                + " -s " + EnginePolicy.quote(agent) + " -R qjs -i -P " + EnginePolicy.quote(json);
    }
}
