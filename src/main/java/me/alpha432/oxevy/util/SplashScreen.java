package me.alpha432.oxevy.util;

import java.io.InputStream;
import java.nio.file.*;

public class SplashScreen {
    public static void show(int durationMs) {
        try {
            InputStream is = SplashScreen.class.getResourceAsStream("/assets/oxevy/textures/splash.png");
            if (is == null) return;

            Path tempImage = Files.createTempFile("oxevy_splash", ".png");
            Files.copy(is, tempImage, StandardCopyOption.REPLACE_EXISTING);
            is.close();

            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                String img = tempImage.toAbsolutePath().toString().replace("\\", "\\\\");
                Path ps = Files.createTempFile("oxevy_splash", ".ps1");
                Files.writeString(ps,
                    "Add-Type -AssemblyName System.Windows.Forms\n" +
                    "Add-Type -AssemblyName System.Drawing\n" +
                    "$f = New-Object System.Windows.Forms.Form\n" +
                    "$f.FormBorderStyle = 'None'\n" +
                    "$f.StartPosition = 'CenterScreen'\n" +
                    "$f.TopMost = $true\n" +
                    "$img = [System.Drawing.Image]::FromFile('" + img + "')\n" +
                    "$f.ClientSize = New-Object System.Drawing.Size($img.Width, $img.Height)\n" +
                    "$pb = New-Object System.Windows.Forms.PictureBox\n" +
                    "$pb.Image = $img\n" +
                    "$pb.Dock = 'Fill'\n" +
                    "$f.Controls.Add($pb)\n" +
                    "$f.Show()\n" +
                    "Start-Sleep -Milliseconds " + durationMs + "\n" +
                    "$f.Close()\n");
                pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", ps.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", "-a", "Preview", tempImage.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder("feh", "-x", "-Z", "--slideshow-delay",
                        String.valueOf(Math.max(1, durationMs / 1000)),
                        tempImage.toAbsolutePath().toString());
            }

            pb.redirectErrorStream(true);
            Process p = pb.start();

            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(durationMs + 500);
                } catch (InterruptedException ignored) {}
                try { p.destroyForcibly(); } catch (Exception ignored) {}
                try { Files.deleteIfExists(tempImage); } catch (Exception ignored) {}
            }, "oxevy-splash-killer");
            killer.setDaemon(true);
            killer.start();
        } catch (Exception ignored) {
        }
    }
}
