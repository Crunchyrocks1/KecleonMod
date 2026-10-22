package dev.rootcatch.nativeengine;

public enum ConnectionStrategy {
    ATTACH("librootcatch-inject.so", "17.22.1",
            "edfcb548f763105f900590a502a8166f4242fcdc7a7a8ab9c0dcfff9d4aa8e18"),
    EARLY_SPAWN("libsilph-spawn.so", "17.23.3",
            "d5000cb822925ee5cc092f3bcd935e7af2a26c656869682326876df87fe1654b"),
    DIRECT_ATTACH("libsilph-spawn.so", "17.23.3",
            "d5000cb822925ee5cc092f3bcd935e7af2a26c656869682326876df87fe1654b"),
    COMPAT_ATTACH("librootcatch-inject.so", "17.22.1",
            "edfcb548f763105f900590a502a8166f4242fcdc7a7a8ab9c0dcfff9d4aa8e18");

    public final String library, version, sha256;
    public static final String LOADER_LIBRARY = "libsilph-loader.so";
    public static final String LOADER_SHA256 = "c3da60fb24f90a9672ec0d0ad13f8df56d17607d2b9cb2c2e8981a59b8d72988";

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
        return "( silph_parent_ns=$(readlink /proc/self/ns/mnt) || exit 90; "
                + "exec unshare -m -- /system/bin/sh " + EnginePolicy.quote(guard)
                + " --probe \"$silph_parent_ns\" )";
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
        return "silph_parent_ns=$(readlink /proc/self/ns/mnt) || exit 90; "
                + "exec unshare -m -- /system/bin/sh " + EnginePolicy.quote(guard)
                + " \"$silph_parent_ns\" " + EnginePolicy.quote(binary)
                + " " + EnginePolicy.quote(directory)
                + " " + EnginePolicy.quote(binary.substring(0, binary.lastIndexOf('/') + 1) + LOADER_LIBRARY)
                + " " + target
                + " -s " + EnginePolicy.quote(agent) + " -R qjs -i -P " + EnginePolicy.quote(json);
    }
}
