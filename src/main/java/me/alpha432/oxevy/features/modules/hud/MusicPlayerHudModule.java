package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.event.impl.input.MouseInputEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.gui.HudEditorScreen;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MusicPlayerHudModule extends HudModule {
    public final Setting<Float> pollRate = num("Poll Rate", 2.0f, 0.5f, 10.0f);

    private final AtomicReference<String> currentTrack = new AtomicReference<>("");
    private final AtomicReference<String> currentArtist = new AtomicReference<>("");
    private final AtomicReference<String> currentStatus = new AtomicReference<>("");
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private Thread pollThread;
    private volatile boolean running;
    private volatile String[] playerctlCommand;
    private volatile String playerctlDisplay = "playerctl";
    private volatile boolean useFlatpakHost;
    private volatile long retryAfterMs;

    // ── Visual constants ────────────────────────────────────────────
    private static final float PILL_HEIGHT = 38.0f;
    private static final float PILL_PADDING = 6.0f;
    private static final float ALBUM_SIZE = 26.0f;
    private static final float MODULE_GAP = 8.0f;
    private static final float PILL_RADIUS = 12.0f;

    private static final int COLOR_BG = 0xCC1A1E24;
    private static final int COLOR_BORDER = 0xFF00FF66;
    private static final int COLOR_ACCENT = 0xFF00FF66;
    private static final int COLOR_TITLE = 0xFFE2E8F0;
    private static final int COLOR_SUBTITLE = 0xFF94A3B8;
    private static final int COLOR_ERROR = 0xFFEF4444;
    private static final int COLOR_CONTROL_ICON = 0xFFCBD5E1;
    private static final int COLOR_CORNER_ACCENT = 0xFF2A2E36;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private static final int EQ_BARS = 6;
    private static final float EQ_BAR_WIDTH = 3.0f;
    private static final float EQ_BAR_GAP = 2.0f;
    private static final float EQ_MAX_HEIGHT = 14.0f;

    // ── Equalizer state ─────────────────────────────────────────────
    private final float[] eqHeights = new float[EQ_BARS];
    private long lastEqUpdate = 0;

    // ── Button hit-test positions (set during draw) ─────────────────
    private float btnPrevX, btnPrevY;
    private float btnPlayCx, btnPlayCy;
    private float btnNextX, btnNextY;
    private float btnLoopX, btnLoopY;
    private float lastPillX, lastPillY, lastPillW;

    // ── OS constants ────────────────────────────────────────────────
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    private static final boolean IS_MAC = OS.contains("mac");
    private static final String PLAYERCTL_FORMAT = "{{artist}}|{{title}}|{{status}}";
    private static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin:/usr/local/sbin:/usr/sbin";
    private static final long GENERIC_RETRY_DELAY_MS = 5_000L;
    private static final long SANDBOX_RETRY_DELAY_MS = 30_000L;
    private static final String[] PLAYERCTL_PATHS = {
            "/usr/bin/playerctl",
            "/usr/local/bin/playerctl",
            "/bin/playerctl",
            "/opt/homebrew/bin/playerctl"
    };
    private static final String[] FLATPAK_SPAWN_PATHS = {
            "/usr/bin/flatpak-spawn",
            "/app/bin/flatpak-spawn",
            "/bin/flatpak-spawn"
    };

    public MusicPlayerHudModule() {
        super("MusicPlayer", "Shows system audio (Spotify, YouTube, etc.) via MPRIS", 240, 40);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BACKEND — completely unchanged
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void onEnable() {
        super.onEnable();
        lastError.set("");
        playerctlCommand = null;
        playerctlDisplay = "playerctl";
        useFlatpakHost = false;
        retryAfterMs = 0L;
        running = true;
        pollThread = new Thread(this::pollLoop, "MusicPlayer-Poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        pollThread = null;
        currentTrack.set("");
        currentArtist.set("");
        currentStatus.set("");
        lastError.set("");
        retryAfterMs = 0L;
    }

    private void pollLoop() {
        while (running) {
            updateTrack();
            try {
                Thread.sleep((long) (pollRate.getValue() * 1000));
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private String resolvePlayerctl() {
        if (isFlatpakSandbox()) {
            String flatpakSpawn = resolveBinary("flatpak-spawn", FLATPAK_SPAWN_PATHS);
            if (flatpakSpawn != null) {
                useFlatpakHost = true;
                playerctlDisplay = flatpakSpawn + " --host playerctl";
                return flatpakSpawn;
            }
        }
        useFlatpakHost = false;
        String playerctl = resolveBinary("playerctl", mergeCandidates(
                PLAYERCTL_PATHS,
                flatpakExportPath(),
                "/var/lib/flatpak/exports/bin/playerctl"));
        if (playerctl != null) {
            playerctlDisplay = playerctl;
            return playerctl;
        }
        playerctlDisplay = "playerctl";
        return "playerctl";
    }

    private void updateTrack() {
        if (isRetryWindowActive()) {
            return;
        }
        try {
            if (IS_LINUX) {
                if (playerctlCommand == null) {
                    String resolved = resolvePlayerctl();
                    if (useFlatpakHost) {
                        playerctlCommand = new String[]{resolved, "--host", "playerctl"};
                    } else {
                        playerctlCommand = new String[]{resolved};
                    }
                }
                updateTrackLinux();
            } else if (IS_WINDOWS) {
                updateTrackWindows();
                lastError.set("");
                retryAfterMs = 0L;
            } else if (IS_MAC) {
                updateTrackMac();
                lastError.set("");
                retryAfterMs = 0L;
            } else {
                clearTrack();
                lastError.set("");
            }
        } catch (Exception e) {
            if (IS_LINUX) {
                handleLinuxException(e);
            } else {
                String message = e.getMessage();
                if (message != null && message.length() > 120) {
                    message = message.substring(0, 117) + "...";
                }
                lastError.set(message == null || message.isBlank() ? e.getClass().getSimpleName() : message);
                retryAfterMs = System.currentTimeMillis() + GENERIC_RETRY_DELAY_MS;
            }
            clearTrack();
        }
    }

    private void updateTrackLinux() throws Exception {
        CommandResult result = runLinuxCommand("metadata", "--format", PLAYERCTL_FORMAT);
        if (result.exitCode != 0) {
            if (isNoPlayerMessage(result.output)) {
                clearTrack();
                lastError.set("");
                retryAfterMs = 0L;
                return;
            }
            if (isSandboxBlockedMessage(result.output)) {
                setRetryError(sandboxErrorMessage(result.output), SANDBOX_RETRY_DELAY_MS);
            } else {
                setRetryError(formatError("playerctl exit " + result.exitCode, result.output), GENERIC_RETRY_DELAY_MS);
            }
            clearTrack();
            return;
        }
        parseLine(firstNonEmptyLine(result.output), "\\|");
        lastError.set("");
        retryAfterMs = 0L;
    }

    private void updateTrackWindows() throws Exception {
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "try{$m=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime];" +
                "$s=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync().GetResults().GetCurrentSession();" +
                "if($s){$p=$s.TryGetMediaPropertiesAsync().GetResults();" +
                "Write-Output ($p.Artist + '|' + $p.Title + '|' + $s.GetPlaybackInfo().PlaybackStatus)}}catch{}")
                .redirectErrorStream(true).start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            process.waitFor();
            parseLine(line, "\\|");
        }
    }

    private void updateTrackMac() throws Exception {
        String[] apps = {"Spotify", "Music", "VLC", "IINA"};
        for (String app : apps) {
            Process process = new ProcessBuilder("osascript", "-e",
                    "tell application \"" + app + "\" to if player state is playing then " +
                    "return (artist of current track) & \"|\" & (name of current track) & \"|\" & player state")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                parseLine(line, "\\|");
                if (!currentTrack.get().isEmpty()) return;
            }
        }
        clearTrack();
    }

    private void parseLine(String line, String separator) {
        if (line != null && !line.isEmpty()) {
            String[] parts = line.split(separator, 3);
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                currentArtist.set(parts[0].isEmpty() ? "Unknown" : parts[0]);
                currentTrack.set(parts[1]);
                currentStatus.set(parts.length >= 3 ? parts[2] : "");
                return;
            }
        }
        clearTrack();
    }

    private void clearTrack() {
        currentTrack.set("");
        currentArtist.set("");
        currentStatus.set("");
    }

    private boolean isFlatpakSandbox() {
        return new File("/.flatpak-info").exists() || System.getenv("FLATPAK_ID") != null;
    }

    private boolean isSnapSandbox() {
        return System.getenv("SNAP") != null || System.getenv("SNAP_NAME") != null;
    }

    private String flatpakExportPath() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            return null;
        }
        return new File(home, ".local/share/flatpak/exports/bin/playerctl").getAbsolutePath();
    }

    private String[] mergeCandidates(String[] base, String... extras) {
        List<String> candidates = new ArrayList<>(base.length + extras.length);
        for (String candidate : base) {
            candidates.add(candidate);
        }
        for (String extra : extras) {
            if (extra != null && !extra.isEmpty()) {
                candidates.add(extra);
            }
        }
        return candidates.toArray(new String[0]);
    }

    private String resolveBinary(String binaryName, String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            File file = new File(candidate);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        try {
            Process which = new ProcessBuilder("/usr/bin/which", binaryName)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(which.getInputStream()))) {
                String line = reader.readLine();
                which.waitFor();
                if (line != null && !line.isEmpty() && new File(line).exists()) {
                    return line;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "command -v " + binaryName);
            pb.environment().put("PATH", DEFAULT_PATH);
            Process sh = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(sh.getInputStream()))) {
                String line = reader.readLine();
                sh.waitFor();
                if (line != null && !line.isEmpty() && new File(line).exists()) {
                    return line;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isRetryWindowActive() {
        return retryAfterMs > System.currentTimeMillis();
    }

    private void setRetryError(String error, long retryDelayMs) {
        lastError.set(error);
        retryAfterMs = System.currentTimeMillis() + retryDelayMs;
    }

    private void handleLinuxException(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().trim();
        if (message.length() > 120) {
            message = message.substring(0, 117) + "...";
        }
        if (isSnapSandbox()) {
            setRetryError(sandboxErrorMessage(message), SANDBOX_RETRY_DELAY_MS);
            return;
        }
        if (isSandboxBlockedMessage(message)) {
            setRetryError(formatError("playerctl=" + playerctlDisplay, message), SANDBOX_RETRY_DELAY_MS);
            return;
        }
        setRetryError("playerctl=" + playerctlDisplay + (message.isEmpty() ? "" : " " + message), GENERIC_RETRY_DELAY_MS);
    }

    private CommandResult runLinuxCommand(String... args) throws Exception {
        List<String> command = new ArrayList<>(playerctlCommand.length + args.length);
        for (String part : playerctlCommand) {
            command.add(part);
        }
        for (String arg : args) {
            command.add(arg);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        String currentPath = System.getenv("PATH");
        pb.environment().put("PATH",
                currentPath == null || currentPath.isBlank() ? DEFAULT_PATH : currentPath + ":" + DEFAULT_PATH);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
        }
        process.waitFor();
        return new CommandResult(process.exitValue(), output.toString().trim());
    }

    private boolean isNoPlayerMessage(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return normalized.contains("no players found")
                || normalized.contains("no player could handle this command");
    }

    private boolean isSandboxBlockedMessage(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return normalized.contains("operation not permitted")
                || normalized.contains("permission denied")
                || normalized.contains("access denied")
                || normalized.contains("could not connect")
                || normalized.contains("dbus")
                || normalized.contains("not allowed");
    }

    private String firstNonEmptyLine(String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    private String formatError(String prefix, String output) {
        if (output == null || output.isBlank()) {
            return prefix + " (no output)";
        }
        String singleLine = output.replace('\n', ' ').trim();
        if (singleLine.length() > 120) {
            singleLine = singleLine.substring(0, 117) + "...";
        }
        return prefix + ": " + singleLine;
    }

    private String sandboxErrorMessage(String detail) {
        String prefix;
        if (isSnapSandbox()) {
            prefix = "Snap sandbox blocked playerctl/MPRIS; use AppImage or native Prism";
        } else if (isFlatpakSandbox()) {
            prefix = "Flatpak sandbox blocked playerctl/MPRIS";
        } else {
            prefix = "playerctl could not reach MPRIS";
        }
        if (detail == null || detail.isBlank()) {
            return prefix;
        }
        return formatError(prefix, detail);
    }

    private record CommandResult(int exitCode, String output) {}

    // ═══════════════════════════════════════════════════════════════════
    //  MEDIA CONTROLS — functional play/pause/next/prev via MPRIS
    // ═══════════════════════════════════════════════════════════════════

    private void sendControlCommand(String... args) {
        try {
            if (IS_LINUX) {
                if (playerctlCommand == null) return;
                List<String> command = new ArrayList<>();
                for (String part : playerctlCommand) command.add(part);
                for (String arg : args) command.add(arg);
                ProcessBuilder pb = new ProcessBuilder(command);
                String currentPath = System.getenv("PATH");
                pb.environment().put("PATH",
                        currentPath == null || currentPath.isBlank() ? DEFAULT_PATH : currentPath + ":" + DEFAULT_PATH);
                pb.redirectErrorStream(true);
                pb.start();
            } else if (IS_WINDOWS) {
                String psCmd = switch (args[0]) {
                    case "play-pause" -> "$s.TryTogglePlayPauseAsync().GetResults()";
                    case "next" -> "$s.TrySkipNextAsync().GetResults()";
                    case "previous" -> "$s.TrySkipPreviousAsync().GetResults()";
                    default -> "";
                };
                if (psCmd.isEmpty()) return;
                new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "try{$s=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]::RequestAsync().GetResults().GetCurrentSession();if($s){" + psCmd + "}}catch{}")
                        .start();
            } else if (IS_MAC) {
                String osaVerb = switch (args[0]) {
                    case "play-pause" -> "playpause";
                    case "next" -> "next track";
                    case "previous" -> "previous track";
                    default -> "";
                };
                if (osaVerb.isEmpty()) return;
                String[] apps = {"Spotify", "Music", "VLC", "IINA"};
                for (String app : apps) {
                    try {
                        new ProcessBuilder("osascript", "-e",
                                "tell application \"" + app + "\" to " + osaVerb).start();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void playPause() { sendControlCommand("play-pause"); }
    private void nextTrack() { sendControlCommand("next"); }
    private void previousTrack() { sendControlCommand("previous"); }

    // ═══════════════════════════════════════════════════════════════════
    //  CLICK HANDLING — wire pill buttons during normal gameplay
    // ═══════════════════════════════════════════════════════════════════

    @Subscribe
    public void onMusicClick(MouseInputEvent e) {
        if (nullCheck()) return;
        if (mc.screen instanceof HudEditorScreen) return;

        if (e.getAction() == 1) {
            int mx = getMouseX();
            int my = getMouseY();

            if (mx < lastPillX || mx > lastPillX + lastPillW) return;
            if (my < lastPillY || my > lastPillY + PILL_HEIGHT) return;

            if (hitCircle(mx, my, btnPlayCx, btnPlayCy, 10)) {
                playPause();
            } else if (hitRect(mx, my, btnPrevX, btnPrevY, 10, 12)) {
                previousTrack();
            } else if (hitRect(mx, my, btnNextX, btnNextY, 10, 12)) {
                nextTrack();
            } else if (hitRect(mx, my, btnLoopX, btnLoopY, 10, 8)) {
                // loop toggle is visual-only for now
            }
        }
    }

    private static boolean hitCircle(int mx, int my, float cx, float cy, float r) {
        float dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static boolean hitRect(int mx, int my, float rx, float ry, float rw, float rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RENDERING — Neon Green / Emerald pixelated pill HUD
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;

        float x = getX();
        float y = getY();
        GuiGraphics ctx = e.getContext();

        String track = currentTrack.get();
        String artist = currentArtist.get();
        String status = currentStatus.get();
        String error = lastError.get();
        boolean hasTrack = !track.isEmpty();
        boolean isPlaying = hasTrack && !"Paused".equalsIgnoreCase(status);
        boolean isPaused = hasTrack && "Paused".equalsIgnoreCase(status);

        updateEqualizer(isPlaying || isPaused);

        String titleText = hasTrack
                ? track.toUpperCase(Locale.ROOT)
                : (error.isEmpty() ? "NO MEDIA" : "NO MEDIA");
        String subtitleText;
        if (hasTrack) {
            subtitleText = artist;
        } else if (!error.isEmpty()) {
            subtitleText = error.length() > 32 ? error.substring(0, 29) + "..." : error;
        } else {
            subtitleText = "";
        }

        float titleWidth = mc.font.width(titleText);
        float subtitleWidth = mc.font.width(subtitleText);
        float centerWidth = Math.max(titleWidth, subtitleWidth);
        float eqWidth = EQ_BARS * EQ_BAR_WIDTH + (EQ_BARS - 1) * EQ_BAR_GAP;
        float controlsWidth = 64;

        float totalWidth = PILL_PADDING + ALBUM_SIZE + MODULE_GAP
                + centerWidth + MODULE_GAP
                + eqWidth + MODULE_GAP
                + controlsWidth + PILL_PADDING;
        totalWidth = Math.max(totalWidth, 220);

        setWidth(totalWidth);
        setHeight(PILL_HEIGHT);

        // 1 ── pill background with green border
        drawBorderedPill(ctx, x, y, totalWidth, PILL_HEIGHT, PILL_RADIUS, COLOR_BG, COLOR_BORDER);

        // 2 ── album cover (white square + green ID card graphic)
        float albumX = x + PILL_PADDING;
        float albumY = y + (PILL_HEIGHT - ALBUM_SIZE) / 2;
        drawAlbumCover(ctx, albumX, albumY);

        // 3 ── track info
        float textX = albumX + ALBUM_SIZE + MODULE_GAP;
        float textBlockH = mc.font.lineHeight * 2;
        float textY = y + (PILL_HEIGHT - textBlockH) / 2;
        ctx.drawString(mc.font, titleText, (int) textX, (int) textY, COLOR_TITLE);
        if (!subtitleText.isEmpty()) {
            int subColor = (error.isEmpty() && hasTrack) ? COLOR_SUBTITLE : COLOR_ERROR;
            ctx.drawString(mc.font, subtitleText, (int) textX, (int) (textY + mc.font.lineHeight), subColor);
        }

        // 4 ── equalizer bars
        float eqX = textX + centerWidth + MODULE_GAP;
        float eqY = y + (PILL_HEIGHT - EQ_MAX_HEIGHT) / 2;
        for (int i = 0; i < EQ_BARS; i++) {
            float barX = eqX + i * (EQ_BAR_WIDTH + EQ_BAR_GAP);
            float barH = eqHeights[i];
            float barY = eqY + EQ_MAX_HEIGHT - barH;
            RenderUtil.rect(ctx, barX, barY, barX + EQ_BAR_WIDTH, barY + barH, COLOR_ACCENT);
        }

        // 5 ── playback controls
        float controlsX = eqX + eqWidth + MODULE_GAP;
        float controlsY = y + (PILL_HEIGHT - 12) / 2;
        drawControls(ctx, controlsX, controlsY, isPlaying);

        // 6 ── corner accent triangle
        drawCornerAccent(ctx, x + totalWidth, y + PILL_HEIGHT);

        // store pill bounds for click hit-testing
        lastPillX = x;
        lastPillY = y;
        lastPillW = totalWidth;
    }

    // ── Equalizer animation ─────────────────────────────────────────

    private void updateEqualizer(boolean active) {
        long now = System.currentTimeMillis();
        if (now - lastEqUpdate < 80) return;
        lastEqUpdate = now;

        for (int i = 0; i < EQ_BARS; i++) {
            if (active) {
                double t = now * 0.003;
                double h = 0;
                h += Math.sin(t * 2.1 + i * 1.3) * 0.3;
                h += Math.sin(t * 3.7 + i * 0.7) * 0.25;
                h += Math.sin(t * 1.4 + i * 2.1) * 0.2;
                h += Math.sin(t * 5.3 + i * 1.8) * 0.15;
                h = (h + 0.9) / 1.8;
                h = Math.max(0.15, Math.min(1.0, h));
                eqHeights[i] = (float) (h * EQ_MAX_HEIGHT);
            } else {
                eqHeights[i] = Math.max(2, eqHeights[i] * 0.9f);
            }
        }
    }

    // ── Pill shape ──────────────────────────────────────────────────

    private void drawBorderedPill(GuiGraphics ctx, float x, float y,
                                  float w, float h, float r,
                                  int bgColor, int borderColor) {
        drawScanlineRoundedRect(ctx, x, y, w, h, r, borderColor);
        drawScanlineRoundedRect(ctx, x + 1, y + 1, w - 2, h - 2,
                Math.max(0, r - 1), bgColor);
    }

    private void drawScanlineRoundedRect(GuiGraphics ctx, float x, float y,
                                         float w, float h, float r, int color) {
        for (float row = 0; row < h; row++) {
            float inset = 0;
            if (row < r) {
                float d = r - row;
                inset = r - (float) Math.sqrt(Math.max(0, r * r - d * d));
            } else if (row > h - r) {
                float d = row - (h - r);
                inset = r - (float) Math.sqrt(Math.max(0, r * r - d * d));
            }
            ctx.fill(
                    (int) (x + inset), (int) (y + row),
                    (int) (x + w - inset), (int) (y + row + 1),
                    color);
        }
    }

    // ── Album cover ─────────────────────────────────────────────────

    private void drawAlbumCover(GuiGraphics ctx, float x, float y) {
        RenderUtil.rect(ctx, x, y, x + ALBUM_SIZE, y + ALBUM_SIZE, COLOR_WHITE);

        float p = 4;
        RenderUtil.rect(ctx, x + p, y + p + 2,
                x + ALBUM_SIZE - p, y + ALBUM_SIZE - p, COLOR_ACCENT);

        float ly = y + p + 5;
        float lp = 3;
        RenderUtil.rect(ctx, x + p + lp, ly,
                x + ALBUM_SIZE - p - lp, ly + 1.5f, COLOR_WHITE);
        RenderUtil.rect(ctx, x + p + lp, ly + 4,
                x + ALBUM_SIZE - p - lp - 3, ly + 5.5f, COLOR_WHITE);
        RenderUtil.rect(ctx, x + p + lp, ly + 8,
                x + ALBUM_SIZE - p - lp - 1, ly + 9.5f, COLOR_WHITE);
    }

    // ── Playback controls ───────────────────────────────────────────

    private void drawControls(GuiGraphics ctx, float x, float y, boolean isPlaying) {
        int icon = COLOR_CONTROL_ICON;

        drawPrevIcon(ctx, x, y, icon);
        btnPrevX = x;
        btnPrevY = y;

        float playCx = x + 16;
        float playCy = y + 6;
        drawPlayPauseButton(ctx, playCx, playCy, isPlaying);
        btnPlayCx = playCx;
        btnPlayCy = playCy;

        drawNextIcon(ctx, x + 28, y, icon);
        btnNextX = x + 28;
        btnNextY = y;

        drawLoopIcon(ctx, x + 42, y, isPlaying ? COLOR_ACCENT : icon);
        btnLoopX = x + 42;
        btnLoopY = y;
    }

    private void drawPrevIcon(GuiGraphics ctx, float x, float y, int color) {
        ctx.fill((int) x, (int) (y + 1), (int) (x + 1.5f), (int) (y + 11), color);
        int[] w = {1, 2, 3, 4, 3, 2, 1};
        for (int i = 0; i < w.length; i++) {
            ctx.fill((int) (x + 3), (int) (y + 3 + i),
                    (int) (x + 3 + w[i]), (int) (y + 4 + i), color);
        }
    }

    private void drawNextIcon(GuiGraphics ctx, float x, float y, int color) {
        int[] w = {4, 3, 2, 1, 2, 3, 4};
        for (int i = 0; i < w.length; i++) {
            ctx.fill((int) x, (int) (y + 3 + i),
                    (int) (x + w[i]), (int) (y + 4 + i), color);
        }
        ctx.fill((int) (x + 5.5f), (int) (y + 1), (int) (x + 7), (int) (y + 11), color);
    }

    private void drawPlayPauseButton(GuiGraphics ctx, float cx, float cy, boolean isPlaying) {
        float radius = 10;
        for (float dy = -radius; dy <= radius; dy++) {
            float hw = (float) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            ctx.fill(
                    (int) (cx - hw), (int) (cy + dy),
                    (int) (cx + hw + 1), (int) (cy + dy + 1),
                    COLOR_ACCENT);
        }

        if (isPlaying) {
            ctx.fill((int) (cx - 3), (int) (cy - 4), (int) (cx - 1), (int) (cy + 5), COLOR_WHITE);
            ctx.fill((int) (cx + 1), (int) (cy - 4), (int) (cx + 3), (int) (cy + 5), COLOR_WHITE);
        } else {
            int[] tw = {1, 3, 5, 7, 5, 3, 1};
            for (int i = 0; i < tw.length; i++) {
                ctx.fill(
                        (int) (cx - 3), (int) (cy - 3 + i),
                        (int) (cx - 3 + tw[i]), (int) (cy - 2 + i),
                        COLOR_WHITE);
            }
        }
    }

    private void drawLoopIcon(GuiGraphics ctx, float x, float y, int color) {
        ctx.fill((int) (x + 1), (int) y, (int) (x + 9), (int) (y + 2), color);
        ctx.fill((int) (x + 7), (int) (y + 2), (int) (x + 9), (int) (y + 7), color);
        ctx.fill((int) (x + 1), (int) (y + 5), (int) (x + 9), (int) (y + 7), color);
        ctx.fill((int) (x + 1), (int) (y + 2), (int) (x + 3), (int) (y + 7), color);
        ctx.fill((int) x, (int) y, (int) (x + 3), (int) (y + 2), color);
    }

    // ── Corner accent ───────────────────────────────────────────────

    private void drawCornerAccent(GuiGraphics ctx, float tipX, float tipY) {
        int size = 6;
        for (int i = 0; i < size; i++) {
            ctx.fill(
                    (int) (tipX - i - 1), (int) (tipY - size + i),
                    (int) (tipX), (int) (tipY - size + i + 1),
                    COLOR_CORNER_ACCENT);
        }
    }
}
