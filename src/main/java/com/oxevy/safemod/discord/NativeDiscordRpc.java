package com.oxevy.safemod.discord;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Loads the native C++ Discord RPC bridge (liboxevy-rpc.so / .dll / .dylib)
 * bundled inside the jar and exposes its functions as JNI natives.
 */
public final class NativeDiscordRpc {
    private static boolean loaded = false;

    static {
        try {
            loadLibrary();
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
    }

    private NativeDiscordRpc() {
    }

    private static void loadLibrary() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String platform;
        String libName;
        String libFile;

        if (os.contains("win")) {
            platform = "windows";
            libName = "oxevy-rpc";
            libFile = "oxevy-rpc.dll";
        } else if (os.contains("mac")) {
            platform = "macos";
            libName = "oxevy-rpc";
            libFile = "liboxevy-rpc.dylib";
        } else {
            platform = "linux";
            libName = "oxevy-rpc";
            libFile = "liboxevy-rpc.so";
        }

        String archDir = switch (arch) {
            case "x86_64", "amd64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };

        String resource = "/oxevy/native/" + platform + "-" + archDir + "/" + libFile;
        try (InputStream is = NativeDiscordRpc.class.getResourceAsStream(resource)) {
            if (is == null) {
                System.loadLibrary(libName);
                return;
            }
            Path tmp = Files.createTempFile("oxevy-" + libName, "-" + libFile);
            Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            System.load(tmp.toAbsolutePath().toString());
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static native boolean initialize(String clientId);

    public static native void updatePresence(String details, String state,
                                             String largeImage, String smallImage,
                                             long startTimestamp, long endTimestamp);

    public static native boolean isRunning();

    public static native void shutdown();
}
