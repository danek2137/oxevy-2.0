package com.oxevy.safemod.music;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Loads the native C++ music bridge (liboxevy-music.so / .dll / .dylib)
 * bundled inside the jar and exposes its JNI entry points. The native library
 * reads the system media player's now-playing info and publishes it to a
 * shared-memory region that {@link MusicSharedMemory} reads.
 *
 * <p>Layout mirrors {@code com.oxevy.safemod.discord.NativeDiscordRpc} so the
 * per-OS / per-arch binary is staged under {@code /oxevy/native/<os>-<arch>}.
 */
public final class NativeMusicLoader {
    private static boolean loaded = false;

    static {
        try {
            loadLibrary();
            // Start the daemon poll thread that writes to shared memory.
            loaded = start();
        } catch (Throwable t) {
            loaded = false;
        }
    }

    private NativeMusicLoader() {
    }

    private static void loadLibrary() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String platform;
        String libName;
        String libFile;

        if (os.contains("win")) {
            platform = "windows";
            libName = "oxevy-music";
            libFile = "oxevy-music.dll";
        } else if (os.contains("mac")) {
            platform = "macos";
            libName = "oxevy-music";
            libFile = "liboxevy-music.dylib";
        } else {
            platform = "linux";
            libName = "oxevy-music";
            libFile = "liboxevy-music.so";
        }

        String archDir = switch (arch) {
            case "x86_64", "amd64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };

        String resource = "/oxevy/native/" + platform + "-" + archDir + "/" + libFile;
        try (InputStream is = NativeMusicLoader.class.getResourceAsStream(resource)) {
            if (is == null) {
                // Fall back to a natively installed lib (e.g. -Djava.library.path).
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
        try {
            if (loaded) {
                if (isRunning()) return true;
                // Native poller thread stopped unexpectedly re-attach.
                loaded = false;
            }
            // Bridge not attached yet (e.g. DBus not ready at JVM start, or the
            // media player only registered on MPRIS a few seconds later). Retry
            // starting the native poller on its own instead of requiring a
            // module toggle.
            loaded = start();
        } catch (Throwable t) {
            loaded = false;
        }
        return loaded;
    }

    /** Start the native background poller. */
    public static native boolean start();

    /** Whether the native poller is currently running. */
    public static native boolean isRunning();

    /** Stop the native poller. */
    public static native void stop();

    /** Force one synchronous refresh; returns true if a track is present. */
    public static native boolean pollNow();

    /** Absolute path of the shared-memory backing file the Java side maps. */
    public static native String getShmFilePath();
}
