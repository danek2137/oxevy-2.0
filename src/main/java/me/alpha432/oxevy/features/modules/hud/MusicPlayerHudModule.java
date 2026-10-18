package me.alpha432.oxevy.features.modules.hud;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.input.MouseInputEvent;
import me.alpha432.oxevy.event.impl.render.Render2DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.gui.HudEditorScreen;
import me.alpha432.oxevy.features.modules.client.ClickGuiModule;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MusicPlayer HUD compact TargetHUD-style card.
 * Real system media via MPRIS (playerctl on Linux, Windows Media Transport
 * Controls, AppleScript on macOS). Small rounded card: cover + title/artist,
 * progress bar with timestamps, and prev / play-pause / next controls.
 */
public class MusicPlayerHudModule extends HudModule {
    public final Setting<Float> pollRate = num("Poll Rate", 2.0f, 0.5f, 10.0f);

    /**
     * The poller feeds both this module's card AND the Dynamic Island's
     * now-playing view. The card is off by default so enabling the poller for
     * the island does not drop a second, duplicate player widget on screen;
     * flip this on to bring the standalone card back.
     */
    public final Setting<Boolean> showCard = bool("Show Card", false);

    // ── live media state (poll thread writes, render thread reads) ──
    private final AtomicReference<String> currentTrack = new AtomicReference<>("");
    private final AtomicReference<String> currentArtist = new AtomicReference<>("");
    private final AtomicReference<String> currentStatus = new AtomicReference<>("");
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private final AtomicLong positionUs = new AtomicLong(0L);
    private final AtomicLong lengthUs = new AtomicLong(0L);
    private final AtomicReference<String> coverUrl = new AtomicReference<>("");

    private Thread pollThread;
    private volatile boolean running;
    private volatile String[] playerctlCommand;
    private volatile boolean useFlatpakHost;
    private volatile long retryAfterMs;

    // ── cover art (poll thread fetches, render thread uploads) ───────
    private final AtomicReference<NativeImage> pendingCover = new AtomicReference<>();
    private volatile String lastFetchedCoverUrl = "";
    private Identifier coverLocation;

    // ── audio source switching (MPRIS players) ────────────────────────
    private volatile String lockedPlayer = null;
    private volatile String[] availablePlayers = new String[0];

    // ── seek slider state ────────────────────────────────────────────
    private boolean seeking;
    private long seekUs;
    private float barX, barY, barW, barH;

    // ── button hit-test positions (set during draw) ─────────────────
    private float btnPrevX, btnPrevY, btnPrevW, btnPrevH;
    private float btnPlayX, btnPlayY, btnPlayW, btnPlayH;
    private float btnNextX, btnNextY, btnNextW, btnNextH;
    private float lastCardX, lastCardY, lastCardW, lastCardH;

    // ── visual constants TargetHUD style ──────────────────────────
    private static final float PAD = 8.0f;
    private static final float CARD_RADIUS = 8.0f;
    private static final float PROGRESS_H = 3.0f;
    private static final float TIME_GAP = 6.0f;
    private static final float COVER_SIZE = 40.0f;
    private static final float MIN_W = 170.0f;
    private static final float MAX_W = 240.0f;

    private static final int COLOR_BG = 0xAA0D0D0D;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SUB = 0xFFA0A0A0;
    private static final int COLOR_DIM = 0xFF777777;
    private static final int COLOR_FAINT = 0xFF888888;
    private static final int COLOR_TRACK = 0x33FFFFFF;
    private static final int COLOR_BTN = 0x22FFFFFF;
    private static final int COLOR_DOT_IDLE = 0xFF444444;
    private static final int COLOR_ERROR = 0xFFE05555;

    // ── OS constants ────────────────────────────────────────────────
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    private static final boolean IS_MAC = OS.contains("mac");
    private static final String PLAYERCTL_FORMAT = "{{artist}}|{{title}}|{{status}}|{{position}}|{{mpris:length}}|{{mpris:artUrl}}";
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
        super("MusicPlayer", "Compact music player with cover, progress and controls", 190, 62);
    }

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void onEnable() {
        super.onEnable();
        Oxevy.LOGGER.info("[MusicPlayer] enabled, starting poller");
        lastError.set("");
        positionUs.set(0L);
        lengthUs.set(0L);
        playerctlCommand = null;
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
        if (pollThread != null) pollThread.interrupt();
        pollThread = null;
        releaseCover();
        pendingCover.set(null);
        clearTrack();
        lastError.set("");
        retryAfterMs = 0L;
    }

    // ════════════════════════════════════════════════════════════════════
    // NOW PLAYING SNAPSHOT
    // ════════════════════════════════════════════════════════════════════

    /**
     * Immutable view of whatever the system media player is doing right now.
     *
     * <p>Every field is copied out of the poller's atomics, so this is safe to
     * call from any thread the web bridge reads it to drive the main-menu
     * "now playing" popup. Nothing here touches GL or the client thread.
     */
    public static final class NowPlaying {
        /** The module is enabled, so the poller is running. */
        public final boolean available;
        /** A track with a non-empty title is reported. */
        public final boolean hasTrack;
        public final boolean playing;
        public final String title;
        public final String artist;
        /** Raw playerctl status, e.g. "Playing" / "Paused". */
        public final String status;
        /** MPRIS art URL a {@code file://} path or an {@code https://} link. */
        public final String artUrl;
        public final long positionUs;
        public final long lengthUs;

        private NowPlaying(boolean available, boolean hasTrack, boolean playing, String title,
                           String artist, String status, String artUrl, long positionUs, long lengthUs) {
            this.available = available;
            this.hasTrack = hasTrack;
            this.playing = playing;
            this.title = title;
            this.artist = artist;
            this.status = status;
            this.artUrl = artUrl;
            this.positionUs = positionUs;
            this.lengthUs = lengthUs;
        }
    }

    /** Snapshot of the track currently reported by the system media player. */
    public NowPlaying nowPlaying() {
        String title = currentTrack.get();
        String artist = currentArtist.get();
        String status = currentStatus.get();
        String art = coverUrl.get();
        return new NowPlaying(
                isEnabled(),
                title != null && !title.isEmpty(),
                status != null && status.equalsIgnoreCase("Playing"),
                title == null ? "" : title,
                artist == null ? "" : artist,
                status == null ? "" : status,
                art == null ? "" : art,
                positionUs.get(),
                lengthUs.get());
    }

    private void pollLoop() {
        Oxevy.LOGGER.info("[MusicPlayer] poller started");
        while (running) {
            updateTrack();
            try {
                Thread.sleep((long) (pollRate.getValue() * 1000));
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MEDIA POLLING client-side local system audio
    // ════════════════════════════════════════════════════════════════════

    private void updateTrack() {
        if (retryAfterMs > System.currentTimeMillis()) return;
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
            refreshCover();
        } catch (Exception e) {
            Oxevy.LOGGER.error("[MusicPlayer] poll error", e);
            handlePollException(e);
            clearTrack();
        }
    }

    private void updateTrackLinux() throws Exception {
        CommandResult listResult = runLinuxCommand("-l");
        if (listResult.exitCode != 0) {
            availablePlayers = new String[0];
            Oxevy.LOGGER.warn("[MusicPlayer] playerctl -l failed exit={} out={}", listResult.exitCode, listResult.output);
            if (isNoPlayerMessage(listResult.output)) {
                clearTrack();
                lastError.set("");
                retryAfterMs = 0L;
                return;
            }
            if (isSandboxBlockedMessage(listResult.output)) {
                setRetryError(sandboxErrorMessage(listResult.output), SANDBOX_RETRY_DELAY_MS);
            } else {
                setRetryError(formatError("playerctl exit " + listResult.exitCode, listResult.output), GENERIC_RETRY_DELAY_MS);
            }
            clearTrack();
            return;
        }
        String output = listResult.output.trim();
        availablePlayers = output.isEmpty() ? new String[0] : output.split("\\R");
        String[] players = availablePlayers;

        // locked source: query exactly that player
        if (lockedPlayer != null) {
            if (containsPlayer(players, lockedPlayer)) {
                CommandResult lockedResult = runLinuxCommand("-p", lockedPlayer, "metadata", "--format", PLAYERCTL_FORMAT);
                if (lockedResult.exitCode == 0) {
                    String line = firstNonEmptyLine(lockedResult.output);
                    if (line != null) {
                        String[] parts = line.split("\\|", 7);
                        if (parts.length >= 2 && !parts[1].isEmpty()) {
                            parseLine(line, "\\|");
                            lastError.set("");
                            retryAfterMs = 0L;
                            return;
                        }
                    }
                }
                clearTrack();
                lastError.set("");
                retryAfterMs = 0L;
                return;
            }
            lockedPlayer = null;
        }

        if (players.length == 0) {
            clearTrack();
            lastError.set("");
            retryAfterMs = 0L;
            return;
        }

        // Scan every player BEFORE the default query: an actively PLAYING
        // player must beat a paused one that merely answers first (e.g. a
        // paused browser tab shadowing Spotify). First playing player in list
        // order wins - deterministic, no flapping between two playing sources.
        String playingLine = null;
        String pausedLine = null;
        for (String player : players) {
            CommandResult result = runLinuxCommand("-p", player, "metadata", "--format", PLAYERCTL_FORMAT);
            if (result.exitCode != 0) {
                Oxevy.LOGGER.warn("[MusicPlayer] query '{}' exit={} out={}", player, result.exitCode, result.output);
                continue;
            }
            String line = firstNonEmptyLine(result.output);
            if (line == null) continue;
            String[] parts = line.split("\\|", 7);
            if (parts.length < 2 || parts[1].isEmpty()) continue;

            if (line.contains("|Playing|")) {
                playingLine = line;
                break;
            }
            if (pausedLine == null) pausedLine = line;
        }

        if (playingLine != null) {
            parseLine(playingLine, "\\|");
            lastError.set("");
            retryAfterMs = 0L;
            return;
        }

        // Nothing is playing: fall back to the default aggregate query, then
        // to the first paused player that still reports a title.
        CommandResult def = runLinuxCommand("metadata", "--format", PLAYERCTL_FORMAT);
        if (def.exitCode == 0) {
            String line = firstNonEmptyLine(def.output);
            if (line != null) {
                String[] parts = line.split("\\|", 7);
                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    parseLine(line, "\\|");
                    lastError.set("");
                    retryAfterMs = 0L;
                    return;
                }
            }
        }

        if (pausedLine != null) {
            parseLine(pausedLine, "\\|");
            lastError.set("");
            retryAfterMs = 0L;
        } else {
            clearTrack();
            lastError.set("");
            retryAfterMs = 0L;
        }
    }

    private void cycleSource() {
        String[] players = availablePlayers;
        if (players == null || players.length == 0) {
            lockedPlayer = null;
            return;
        }
        if (lockedPlayer == null) {
            lockedPlayer = players[0];
        } else {
            int idx = -1;
            for (int i = 0; i < players.length; i++) {
                if (lockedPlayer.equals(players[i])) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1 || idx >= players.length - 1) {
                lockedPlayer = null;
            } else {
                lockedPlayer = players[idx + 1];
            }
        }
        lastError.set("");
        retryAfterMs = 0L;
    }

    private static boolean containsPlayer(String[] players, String target) {
        for (String player : players) {
            if (target.equals(player)) return true;
        }
        return false;
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
            String[] parts = line.split(separator, 7);
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                currentArtist.set(parts[0].isEmpty() ? "Unknown" : parts[0]);
                currentTrack.set(parts[1]);
                currentStatus.set(parts.length >= 3 ? parts[2] : "");
                positionUs.set(parts.length >= 4 ? parseUs(parts[3]) : 0L);
                lengthUs.set(parts.length >= 5 ? parseUs(parts[4]) : 0L);
                coverUrl.set(parts.length >= 6 ? (parts[5] == null ? "" : parts[5].trim()) : "");
                return;
            }
        }
        clearTrack();
    }

    private long parseUs(String part) {
        if (part == null || part.isBlank()) return 0L;
        String trimmed = part.trim();
        if (trimmed.startsWith("{{")) return 0L;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void clearTrack() {
        currentTrack.set("");
        currentArtist.set("");
        currentStatus.set("");
        positionUs.set(0L);
        lengthUs.set(0L);
        coverUrl.set("");
    }

    // ── cover art loading ─────────────────────────────────────────

    private void refreshCover() {
        String url = coverUrl.get();
        if (Objects.equals(url, lastFetchedCoverUrl)) return;
        lastFetchedCoverUrl = url;
        if (url == null || url.isEmpty()) {
            pendingCover.set(null);
            return;
        }
        try {
            pendingCover.set(fetchCoverImage(url));
        } catch (Exception ex) {
            Oxevy.LOGGER.warn("[MusicPlayer] failed to load cover from {}: {}", url, ex.getMessage());
        }
    }

    private NativeImage fetchCoverImage(String url) throws Exception {
        if (url.startsWith("file://")) {
            try (InputStream is = Files.newInputStream(Path.of(URI.create(url)))) {
                return NativeImage.read(is);
            }
        }
        URLConnection conn = new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        try (InputStream is = conn.getInputStream()) {
            return NativeImage.read(is);
        }
    }

    private void consumeCoverTexture() {
        NativeImage pending = pendingCover.getAndSet(null);
        if (pending == null) {
            if (coverLocation != null && coverUrl.get().isEmpty()) {
                releaseCover();
            }
            return;
        }
        releaseCover();
        DynamicTexture texture = new DynamicTexture(() -> "music_cover", pending);
        texture.upload();
        coverLocation = Identifier.fromNamespaceAndPath("oxevy", "music_cover");
        mc.getTextureManager().register(coverLocation, texture);
    }

    private void releaseCover() {
        if (coverLocation != null) {
            try {
                mc.getTextureManager().release(coverLocation);
            } catch (Throwable ignored) {
            }
            coverLocation = null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // TRANSPORT CONTROLS play/pause/next/prev via MPRIS
    // ════════════════════════════════════════════════════════════════════

    private void sendControlCommand(String... args) {
        try {
            if (IS_LINUX) {
                if (playerctlCommand == null) return;
                List<String> command = new ArrayList<>(playerctlCommand.length + args.length);
                for (String part : playerctlCommand) command.add(part);
                if (lockedPlayer != null) {
                    command.add("-p");
                    command.add(lockedPlayer);
                }
                for (String arg : args) command.add(arg);
                ProcessBuilder pb = new ProcessBuilder(command);
                String currentPath = System.getenv("PATH");
                pb.environment().put("PATH",
                        currentPath == null || currentPath.isBlank() ? DEFAULT_PATH : currentPath + ":" + DEFAULT_PATH);
                prepareChildEnv(pb);
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

    private void seekTo(long targetUs) {
        double seconds = targetUs / 1_000_000.0;
        Oxevy.LOGGER.info("[MusicPlayer] seek to {}s", seconds);
        try {
            if (IS_LINUX) {
                if (playerctlCommand == null) return;
                List<String> command = new ArrayList<>(playerctlCommand.length + 2);
                for (String part : playerctlCommand) command.add(part);
                if (lockedPlayer != null) {
                    command.add("-p");
                    command.add(lockedPlayer);
                }
                command.add("position");
                command.add(String.format(Locale.ROOT, "%.3f", seconds));
                ProcessBuilder pb = new ProcessBuilder(command);
                String currentPath = System.getenv("PATH");
                pb.environment().put("PATH",
                        currentPath == null || currentPath.isBlank() ? DEFAULT_PATH : currentPath + ":" + DEFAULT_PATH);
                prepareChildEnv(pb);
                pb.redirectErrorStream(true);
                pb.start();
            } else if (IS_WINDOWS) {
                long ticks = targetUs * 10;
                new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "try{$s=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]::RequestAsync().GetResults().GetCurrentSession();if($s){$s.TryChangePlaybackPositionAsync(" + ticks + ").GetResults()}}catch{}")
                        .start();
            } else if (IS_MAC) {
                String[] apps = {"Spotify", "Music", "VLC", "IINA"};
                for (String app : apps) {
                    try {
                        new ProcessBuilder("osascript", "-e",
                                "tell application \"" + app + "\" to set player position to " + String.format(Locale.ROOT, "%.3f", seconds))
                                .start();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CLICK HANDLING
    // ════════════════════════════════════════════════════════════════════

    @Subscribe
    public void onMusicClick(MouseInputEvent e) {
        if (nullCheck()) return;
        if (!showCard.getValue()) return; // invisible card must not eat clicks
        if (mc.screen instanceof HudEditorScreen) return;
        if (e.getAction() != 1) return;

        int mx = getMouseX();
        int my = getMouseY();
        if (mx < lastCardX || mx > lastCardX + lastCardW) return;
        if (my < lastCardY || my > lastCardY + lastCardH) return;

        if (hitRect(mx, my, btnPlayX, btnPlayY, btnPlayW, btnPlayH)) {
            playPause();
        } else if (hitRect(mx, my, btnPrevX, btnPrevY, btnPrevW, btnPrevH)) {
            previousTrack();
        } else if (hitRect(mx, my, btnNextX, btnNextY, btnNextW, btnNextH)) {
            nextTrack();
        } else if (lengthUs.get() > 0 && hitRect(mx, my, barX, barY - 4, barW, barH + 8)) {
            // progress bar: seek is handled by the drag logic, ignore click
        } else {
            cycleSource();
        }
    }

    private static boolean hitRect(int mx, int my, float rx, float ry, float rw, float rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private void handleSeekDrag(boolean hasTrack, long lenUs) {
        if (mc.screen instanceof HudEditorScreen) {
            seeking = false;
            return;
        }
        boolean leftDown = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        int mx = getMouseX();
        int my = getMouseY();
        if (leftDown) {
            boolean onBar = hasTrack && lenUs > 0
                    && my >= barY - 4 && my <= barY + barH + 4
                    && mx >= barX - 4 && mx <= barX + barW + 4;
            if (seeking || onBar) {
                seeking = true;
                if (lenUs > 0) {
                    float t = (mx - barX) / barW;
                    seekUs = Math.max(0L, Math.min(lenUs, (long) (t * lenUs)));
                }
            }
        } else if (seeking) {
            seeking = false;
            if (hasTrack && lenUs > 0) seekTo(seekUs);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // RENDERING compact TargetHUD-style card
    // ════════════════════════════════════════════════════════════════════

    private int accent() {
        ClickGuiModule gui = ClickGuiModule.getInstance();
        if (gui != null && gui.color.getValue() != null) return gui.color.getValue().getRGB();
        return 0xFF00FF00;
    }

    @Override
    public void drawContent(Render2DEvent e) {
        if (nullCheck()) return;
        // Card hidden: the poller still runs (it feeds the Dynamic Island and
        // the web bridge), nothing is drawn and no seek/hit state is touched.
        if (!showCard.getValue()) return;

        float x = getX();
        float y = getY();
        GuiGraphics ctx = e.getContext();

        String track = currentTrack.get();
        String artist = currentArtist.get();
        String status = currentStatus.get();
        String error = lastError.get();
        boolean hasTrack = !track.isEmpty();
        boolean isPlaying = hasTrack && !"Paused".equalsIgnoreCase(status);

        consumeCoverTexture();
        boolean hasCover = coverLocation != null;

        long posUs = positionUs.get();
        long lenUs = lengthUs.get();

        float lineH = mc.font.lineHeight;
        float timeW = Math.max(mc.font.width("0:00"), mc.font.width("99:99"));
        float textStart = x + PAD + (hasCover ? COVER_SIZE + 8.0f : 0.0f);

        String titleText = hasTrack ? track : "No media";
        float minBarW = 56.0f;
        float width = Math.max(MIN_W, Math.min(MAX_W,
                textStart + Math.max(mc.font.width(titleText), timeW * 2 + TIME_GAP * 2 + minBarW) + PAD));
        float rightEdge = x + width - PAD;
        float textMaxW = rightEdge - textStart;

        float y0 = y + 6;
        float titleY = y0;
        float artistY = y0 + lineH;
        float barY = y0 + lineH * 2 + 4;
        float controlsY = barY + lineH + 6;
        float height = Math.max(COVER_SIZE + 2 * PAD + 2, (controlsY + 14 + 6) - y);

        setWidth(width);
        setHeight(height);

        // card background
        RenderUtil.roundRect(ctx, x, y, width, height, CARD_RADIUS, COLOR_BG);

        // cover art
        if (hasCover) {
            float coverX = x + PAD;
            float coverY = y + PAD;
            ctx.blit(RenderPipelines.GUI_TEXTURED, coverLocation,
                    (int) coverX, (int) coverY, 0.0f, 0.0f,
                    (int) COVER_SIZE, (int) COVER_SIZE, (int) COVER_SIZE, (int) COVER_SIZE);
            RenderUtil.rect(ctx, coverX - 1, coverY - 1, coverX + COVER_SIZE + 1, coverY + COVER_SIZE + 1,
                    COLOR_BG, 1.0f);
        }

        // title + status dot
        String source = lockedPlayer;
        String chipText = null;
        float chipW = 0;
        if (source != null) {
            chipText = truncate(source, Math.max(10, textMaxW * 0.5f));
            chipW = mc.font.width(chipText) + 10;
        }
        String shownTitle = truncate(titleText, Math.max(0, textMaxW - chipW - 6));
        ctx.drawString(mc.font, shownTitle, (int) textStart, (int) titleY, hasTrack ? COLOR_TEXT : COLOR_DIM);
        if (hasTrack) {
            float dotX = textStart + mc.font.width(shownTitle) + 6;
            int dotColor = isPlaying ? accent() : COLOR_DOT_IDLE;
            RenderUtil.roundRect(ctx, dotX, titleY + lineH / 2.0f - 2.0f, 4.0f, 4.0f, 2.0f, dotColor);
        }
        if (chipText != null) {
            RenderUtil.roundRect(ctx, rightEdge - chipW, titleY - 1, chipW, lineH + 2, 4.0f, COLOR_BTN);
            ClickGuiModule gui = ClickGuiModule.getInstance();
            int chipColor = (gui != null && gui.color.getValue() != null) ? gui.color.getValue().getRGB() : 0xFF00FF00;
            ctx.drawString(mc.font, chipText, (int) (rightEdge - chipW + 5), (int) (titleY + 1), chipColor);
        }

        // artist / idle hint
        if (hasTrack) {
            if (!artist.isEmpty()) {
                ctx.drawString(mc.font, truncate(artist, textMaxW), (int) textStart, (int) artistY, COLOR_SUB);
            }
        } else if (error.isEmpty()) {
            ctx.drawString(mc.font, "No media playing", (int) textStart, (int) artistY, COLOR_DIM);
        } else {
            ctx.drawString(mc.font, truncate(error, textMaxW), (int) textStart, (int) artistY, COLOR_ERROR);
        }

        // progress bar + timestamps
        String elapsed = formatTime(posUs);
        String total = lenUs > 0 ? formatTime(lenUs) : "0:00";
        float elapsedW = mc.font.width(elapsed);
        float totalW = mc.font.width(total);
        float barX = textStart + elapsedW + TIME_GAP;
        float barMaxW = rightEdge - totalW - TIME_GAP - barX;
        float bY = barY + (lineH - PROGRESS_H) / 2.0f;
        this.barX = barX;
        this.barY = bY;
        this.barW = barMaxW;
        this.barH = PROGRESS_H;
        handleSeekDrag(hasTrack, lenUs);
        long displayPos = seeking ? seekUs : posUs;
        String displayElapsed = formatTime(displayPos);
        RenderUtil.roundRect(ctx, barX, bY, barMaxW, PROGRESS_H, 2.0f, COLOR_TRACK);
        float frac = (lenUs > 0 && displayPos > 0) ? (float) Math.min(1.0, (double) displayPos / lenUs) : 0.0f;
        if (frac > 0.01f) {
            RenderUtil.roundRect(ctx, barX, bY, Math.max(barMaxW * frac, 3.0f), PROGRESS_H, 2.0f, accent());
        }
        if (seeking) {
            RenderUtil.roundRect(ctx, barX + barMaxW * frac - 3.0f, bY - 3.0f, 6.0f, PROGRESS_H + 6.0f, 3.0f, COLOR_TEXT);
        }
        ctx.drawString(mc.font, displayElapsed, (int) textStart, (int) barY, COLOR_FAINT);
        ctx.drawString(mc.font, total, (int) (rightEdge - totalW), (int) barY, COLOR_FAINT);

        // transport controls
        drawControls(ctx, x, width, controlsY, isPlaying);

        lastCardX = x;
        lastCardY = y;
        lastCardW = width;
        lastCardH = height;
    }

    private void drawControls(GuiGraphics ctx, float x, float W, float controlsY, boolean isPlaying) {
        float bw = 16.0f;
        float bh = 14.0f;
        float gap = 8.0f;
        float groupW = bw * 3 + gap * 2;
        float sx = x + (W - groupW) / 2.0f;
        float sy = controlsY;
        int iconColor = COLOR_TEXT;
        int accent = accent();

        btnPrevX = sx;
        btnPrevY = sy;
        btnPrevW = bw;
        btnPrevH = bh;
        RenderUtil.roundRect(ctx, sx, sy, bw, bh, 4.0f, COLOR_BTN);
        drawPrevIcon(ctx, sx + bw / 2.0f, sy + bh / 2.0f, iconColor);

        btnPlayX = sx + bw + gap;
        btnPlayY = sy;
        btnPlayW = bw;
        btnPlayH = bh;
        RenderUtil.roundRect(ctx, btnPlayX, sy, bw, bh, 4.0f, isPlaying ? accent : COLOR_BTN);
        if (isPlaying) {
            drawPauseIcon(ctx, btnPlayX + bw / 2.0f, sy + bh / 2.0f, 0xFF0D0D0D);
        } else {
            drawPlayIcon(ctx, btnPlayX + bw / 2.0f, sy + bh / 2.0f, iconColor);
        }

        btnNextX = sx + bw * 2 + gap * 2;
        btnNextY = sy;
        btnNextW = bw;
        btnNextH = bh;
        RenderUtil.roundRect(ctx, btnNextX, sy, bw, bh, 4.0f, COLOR_BTN);
        drawNextIcon(ctx, btnNextX + bw / 2.0f, sy + bh / 2.0f, iconColor);
    }

    // ── small flat icons (7x7 pixel art, mirror-symmetric) ──────────
    // each cell renders as ICON_SCALE px so the 7x7 grid becomes a chunky
    // 14x14 icon that fills the button instead of a 7px smear.

    private static final int ICON_SCALE = 2;

    private static final boolean[][] ICON_PREV = {
        {true, false, false, false, false, false, true},
        {true, true, false, false, false, false, true},
        {true, true, true, false, false, false, true},
        {true, true, true, true, false, false, true},
        {true, true, true, false, false, false, true},
        {true, true, false, false, false, false, true},
        {true, false, false, false, false, false, true},
    };

    private static final boolean[][] ICON_NEXT = {
        {true, false, false, false, false, false, true},
        {true, false, false, false, false, true, true},
        {true, false, false, false, true, true, true},
        {true, false, false, true, true, true, true},
        {true, false, false, false, true, true, true},
        {true, false, false, false, false, true, true},
        {true, false, false, false, false, false, true},
    };

    private static final boolean[][] ICON_PLAY = {
        {false, false, false, false, false, true, false},
        {false, false, false, false, true, true, false},
        {false, false, false, true, true, true, false},
        {false, false, true, true, true, true, false},
        {false, false, false, true, true, true, false},
        {false, false, false, false, true, true, false},
        {false, false, false, false, false, true, false},
    };

    private static final boolean[][] ICON_PAUSE = {
        {false, true, true, false, true, true, false},
        {false, true, true, false, true, true, false},
        {false, true, true, false, true, true, false},
        {false, true, true, false, true, true, false},
        {false, true, true, false, true, true, false},
        {false, true, true, false, true, true, false},
        {false, true, true, false, true, true, false},
    };

    private void drawPrevIcon(GuiGraphics ctx, float cx, float cy, int color) {
        drawPixelIcon(ctx, cx, cy, ICON_PREV, color);
    }

    private void drawNextIcon(GuiGraphics ctx, float cx, float cy, int color) {
        drawPixelIcon(ctx, cx, cy, ICON_NEXT, color);
    }

    private void drawPlayIcon(GuiGraphics ctx, float cx, float cy, int color) {
        drawPixelIcon(ctx, cx, cy, ICON_PLAY, color);
    }

    private void drawPauseIcon(GuiGraphics ctx, float cx, float cy, int color) {
        drawPixelIcon(ctx, cx, cy, ICON_PAUSE, color);
    }

    private void drawPixelIcon(GuiGraphics ctx, float cx, float cy, boolean[][] grid, int color) {
        int cols = grid[0].length * ICON_SCALE;
        int rows = grid.length * ICON_SCALE;
        int ox = Math.round(cx) - cols / 2;
        int oy = Math.round(cy) - rows / 2;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col]) {
                    ctx.fill(ox + col * ICON_SCALE, oy + row * ICON_SCALE,
                            ox + col * ICON_SCALE + ICON_SCALE,
                            oy + row * ICON_SCALE + ICON_SCALE, color);
                }
            }
        }
    }

    private String truncate(String s, float maxW) {
        if (s == null || s.isEmpty() || mc.font.width(s) <= maxW) return s;
        String ellipsis = "...";
        float ellW = mc.font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String next = sb.toString() + s.charAt(i);
            if (mc.font.width(next) + ellW > maxW) break;
            sb.append(s.charAt(i));
        }
        return sb.toString() + ellipsis;
    }

    private String formatTime(long micros) {
        long totalSecs = Math.max(0, micros / 1_000_000L);
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        return String.format(Locale.ROOT, "%d:%02d", mins, secs);
    }

    // ════════════════════════════════════════════════════════════════════
    // PLAYERCTL RESOLUTION flatpak/snap sandbox aware
    // ════════════════════════════════════════════════════════════════════

    private String resolvePlayerctl() {
        if (isFlatpakSandbox()) {
            String flatpakSpawn = resolveBinary("flatpak-spawn", FLATPAK_SPAWN_PATHS);
            if (flatpakSpawn != null) {
                useFlatpakHost = true;
                return flatpakSpawn;
            }
        }
        useFlatpakHost = false;
        String playerctl = resolveBinary("playerctl", mergeCandidates(
                PLAYERCTL_PATHS,
                flatpakExportPath(),
                "/var/lib/flatpak/exports/bin/playerctl"));
        return playerctl != null ? playerctl : "playerctl";
    }

    private boolean isFlatpakSandbox() {
        return new File("/.flatpak-info").exists() || System.getenv("FLATPAK_ID") != null;
    }

    private boolean isSnapSandbox() {
        return System.getenv("SNAP") != null || System.getenv("SNAP_NAME") != null;
    }

    private String flatpakExportPath() {
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) return null;
        return new File(home, ".local/share/flatpak/exports/bin/playerctl").getAbsolutePath();
    }

    private String[] mergeCandidates(String[] base, String... extras) {
        List<String> candidates = new ArrayList<>(base.length + extras.length);
        for (String candidate : base) candidates.add(candidate);
        for (String extra : extras) {
            if (extra != null && !extra.isEmpty()) candidates.add(extra);
        }
        return candidates.toArray(new String[0]);
    }

    private String resolveBinary(String binaryName, String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            File file = new File(candidate);
            if (file.exists() && file.canExecute()) return file.getAbsolutePath();
        }
        try {
            Process which = new ProcessBuilder("/usr/bin/which", binaryName)
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(which.getInputStream()))) {
                String line = reader.readLine();
                which.waitFor();
                if (line != null && !line.isEmpty() && new File(line).exists()) return line;
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
                if (line != null && !line.isEmpty() && new File(line).exists()) return line;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private CommandResult runLinuxCommand(String... args) throws Exception {
        List<String> command = new ArrayList<>(playerctlCommand.length + args.length);
        for (String part : playerctlCommand) command.add(part);
        for (String arg : args) command.add(arg);
        ProcessBuilder pb = new ProcessBuilder(command);
        String currentPath = System.getenv("PATH");
        pb.environment().put("PATH",
                currentPath == null || currentPath.isBlank() ? DEFAULT_PATH : currentPath + ":" + DEFAULT_PATH);
        prepareChildEnv(pb);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
            }
        }
        process.waitFor();
        return new CommandResult(process.exitValue(), output.toString().trim());
    }

    private void prepareChildEnv(ProcessBuilder pb) {
        java.util.Map<String, String> env = pb.environment();
        String bus = env.get("DBUS_SESSION_BUS_ADDRESS");
        if (bus == null || bus.isBlank()) {
            String runtime = env.get("XDG_RUNTIME_DIR");
            if (runtime == null || runtime.isBlank()) {
                runtime = "/run/user/" + currentUid();
            }
            if (runtime != null && !runtime.isBlank()) {
                File busFile = new File(runtime, "bus");
                if (busFile.isFile()) {
                    env.put("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + busFile.getAbsolutePath());
                }
            }
        }
    }

    private String currentUid() {
        try {
            Process process = new ProcessBuilder("id", "-u").redirectErrorStream(true).start();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                line = reader.readLine();
            }
            process.waitFor();
            if (line != null && !line.trim().isEmpty()) return line.trim();
        } catch (Exception ignored) {
        }
        return "";
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
        if (output == null || output.isEmpty()) return null;
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) return line;
        }
        return null;
    }

    private String formatError(String prefix, String output) {
        if (output == null || output.isBlank()) return prefix + " (no output)";
        String singleLine = output.replace('\n', ' ').trim();
        if (singleLine.length() > 120) singleLine = singleLine.substring(0, 117) + "...";
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
        if (detail == null || detail.isBlank()) return prefix;
        return formatError(prefix, detail);
    }

    private void setRetryError(String error, long retryDelayMs) {
        lastError.set(error);
        retryAfterMs = System.currentTimeMillis() + retryDelayMs;
    }

    private void handlePollException(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().trim();
        if (message.length() > 120) message = message.substring(0, 117) + "...";
        if (isSnapSandbox()) {
            setRetryError(sandboxErrorMessage(message), SANDBOX_RETRY_DELAY_MS);
            return;
        }
        if (isSandboxBlockedMessage(message)) {
            setRetryError(formatError("playerctl=" + (useFlatpakHost ? "flatpak-spawn" : "playerctl"), message), SANDBOX_RETRY_DELAY_MS);
            return;
        }
        setRetryError("playerctl=" + (useFlatpakHost ? "flatpak-spawn" : "playerctl")
                + (message.isEmpty() ? "" : " " + message), GENERIC_RETRY_DELAY_MS);
    }

    private record CommandResult(int exitCode, String output) {}
}