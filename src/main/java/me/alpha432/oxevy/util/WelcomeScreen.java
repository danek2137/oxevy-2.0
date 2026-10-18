package me.alpha432.oxevy.util;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Frameless "Welcome back" splash shown over the game while it finishes loading.
 * Pure Swing so it works on every Minecraft version this mod supports.
 *
 * Design: dark near-black background (#0D0D0D), rounded #111111 card with an
 * 18px corner radius (RoundRectangle2D) and a soft drop shadow painted with
 * AlphaComposite layers in paintComponent(), rendered with antialiasing.
 */
public class WelcomeScreen {

    private static final int WINDOW_W = 470;
    private static final int WINDOW_H = 340;

    private static final int CARD_X = 30;
    private static final int CARD_Y = 34;
    private static final int CARD_W = WINDOW_W - 60;
    private static final int CARD_H = WINDOW_H - 68;
    private static final int CORNER_RADIUS = 18;

    private static final int HEAD_SIZE = 112;
    private static final int SHADOW_BLUR = 14;

    private static final long FADE_MS = 320;
    private static final long TOTAL_MS = 3900;

    private static final Color BG_COLOR = new Color(0x0D0D0D);
    private static final Color CARD_COLOR = new Color(0x111111);
    private static final Color CARD_BORDER = new Color(255, 255, 255, 13);
    private static final Color ACCENT = new Color(0x00FF66);
    private static final Color TEXT_DIM = new Color(0x94A3B8);
    private static final Color TEXT_DARKER = new Color(0x475569);
    private static final Color TRACK_COLOR = new Color(0x1E1E1E);

    private static volatile JWindow window;
    private static volatile Font displayFont;

    private WelcomeScreen() {
    }

    /** Shows the welcome overlay on the Swing EDT. Only one instance at a time. */
    public static void show(String username, String uuid) {
        if (username == null || username.isEmpty()) return;
        try {
            System.setProperty("java.awt.headless", "false");
        } catch (Exception ignored) {
        }
        if (GraphicsEnvironment.isHeadless()) {
            me.alpha432.oxevy.Oxevy.LOGGER.warn("WelcomeScreen: headless environment, welcome overlay skipped");
            return;
        }
        synchronized (WelcomeScreen.class) {
            if (window != null && window.isShowing()) return;
            window = null;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                if (displayFont == null) {
                    displayFont = loadFont();
                }
            } catch (Exception ignored) {
            }

            final AtomicReference<BufferedImage> head = new AtomicReference<>();
            final WelcomePanel panel = new WelcomePanel(username, head);
            JWindow win = new JWindow();
            win.setType(java.awt.Window.Type.UTILITY);
            win.setContentPane(panel);
            win.setSize(WINDOW_W, WINDOW_H);
            win.setLocationRelativeTo(null);
            win.setAlwaysOnTop(true);
            win.setBackground(BG_COLOR);
            win.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            win.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    close(win);
                }
            });

            synchronized (WelcomeScreen.class) {
                window = win;
            }
            win.setVisible(true);
            win.toFront();

            loadHeadAsync(username, uuid, head, panel);

            final long startedAt = System.nanoTime();
            Timer animator = new Timer(16, e -> {
                long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;
                panel.setProgress(Math.min(1f, elapsed / (float) TOTAL_MS));
                panel.setFade(Math.min(1f, elapsed / (float) FADE_MS));
                panel.repaint();
                if (elapsed >= TOTAL_MS) {
                    ((Timer) e.getSource()).stop();
                    close(win);
                }
            });
            animator.setRepeats(true);
            animator.start();
            panel.setAnimatorSafe(animator);
            Timer frontTimer = new Timer(70, ev -> {
                if (win.isShowing()) {
                    win.toFront();
                    ((Timer) ev.getSource()).stop();
                }
            });
            frontTimer.setRepeats(true);
            frontTimer.start();
        });
    }

    private static void close(JWindow win) {
        if (win == null) return;
        SwingUtilities.invokeLater(() -> {
            JPanel panel = (JPanel) win.getContentPane();
            if (panel instanceof WelcomePanel) {
                WelcomePanel welcome = (WelcomePanel) panel;
                welcome.stopTimer();
                welcome.animator = null;
            }
            win.dispose();
            synchronized (WelcomeScreen.class) {
                if (window == win) window = null;
            }
        });
    }

    private static void loadHeadAsync(String username, String uuid, AtomicReference<BufferedImage> head, JPanel panel) {
        new Thread(() -> {
            try {
                String ref = uuid != null && !uuid.isEmpty() ? uuid : URLEncoder.encode(username, StandardCharsets.UTF_8);
                URL url = new URL("https://mc-heads.net/avatar/" + ref + "/128");
                java.net.URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                BufferedImage img = ImageIO.read(conn.getInputStream());
                if (img != null) {
                    head.set(img);
                    SwingUtilities.invokeLater(panel::repaint);
                }
            } catch (Exception ignored) {
            }
        }, "OxevyWelcomeHead").start();
    }

    private static Font loadFont() {
        try (InputStream is = WelcomeScreen.class.getResourceAsStream("/assets/oxevy/fonts/dynapuff-variablefont_wdth_wght.ttf")) {
            if (is != null) {
                Font base = Font.createFont(Font.TRUETYPE_FONT, is);
                return base.deriveFont(Font.BOLD, 34f);
            }
        } catch (Exception ignored) {
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 34);
    }

    private static final class WelcomePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final String username;
        private final AtomicReference<BufferedImage> head;
        private volatile float fade = 0f;
        private volatile float progress = 0f;
        private volatile Timer animator;

        WelcomePanel(String username, AtomicReference<BufferedImage> head) {
            this.username = username;
            this.head = head;
            setOpaque(true);
            setBackground(BG_COLOR);
        }

        void setAnimatorSafe(Timer t) {
            this.animator = t;
        }

        void stopTimer() {
            if (animator != null) animator.stop();
        }

        void setFade(float v) {
            this.fade = v;
        }

        void setProgress(float v) {
            this.progress = v;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Near-black backdrop
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintShadow(g2);

            // Card
            RoundRectangle2D card = new RoundRectangle2D.Float(CARD_X, CARD_Y, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS);
            g2.setColor(CARD_COLOR);
            g2.fill(card);
            g2.setColor(CARD_BORDER);
            g2.draw(card);

            // Accent top notch
            g2.setColor(ACCENT);
            RoundRectangle2D notch = new RoundRectangle2D.Float(CARD_X + 24, CARD_Y, CARD_W - 48, 3, 3, 3);
            g2.fill(notch);

            // Head
            int hx = (getWidth() - HEAD_SIZE) / 2;
            int hy = CARD_Y + 26;
            paintHead(g2, hx, hy);

            // "WELCOME BACK" label
            drawSpaced(g2, "WELCOME BACK", 15, hx + HEAD_SIZE / 2f, hy + HEAD_SIZE + 22, 4f, TEXT_DIM);

            // Username
            Font nameFont = displayFont != null ? displayFont : new Font(Font.SANS_SERIF, Font.BOLD, 34);
            float nameW = g2.getFontMetrics(nameFont).stringWidth(username);
            g2.setFont(nameFont);
            g2.setColor(ACCENT);
            g2.drawString(username, (getWidth() - nameW) / 2f, hy + HEAD_SIZE + 52);

            // Accent underline under the name
            float ulY = hy + HEAD_SIZE + 62;
            float ulW = Math.min(nameW, 140);
            g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 90));
            g2.fill(new RoundRectangle2D.Float((getWidth() - ulW) / 2f, ulY, ulW, 2, 1, 1));

            // Subtitle
            drawSpaced(g2, "OXEVY IS LOADING", 11, getWidth() / 2f, ulY + 18, 3f, TEXT_DARKER);

            // Progress track
            int pw = CARD_W - 60;
            int px = CARD_X + 30;
            int py = CARD_Y + CARD_H - 30;
            g2.setColor(TRACK_COLOR);
            g2.fill(new RoundRectangle2D.Float(px, py, pw, 5, 3, 3));
            g2.setColor(ACCENT);
            g2.fill(new RoundRectangle2D.Float(px, py, Math.max(0, pw * clamp01(progress)), 5, 3, 3));

            g2.dispose();
        }

        private float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private void paintShadow(Graphics2D g2) {
            for (int i = 0; i < SHADOW_BLUR; i++) {
                float a = 0.04f * (i + 1) * fade;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                g2.setColor(new Color(0, 0, 0));
                RoundRectangle2D r = new RoundRectangle2D.Float(
                    CARD_X + i - 4,
                    CARD_Y + i + 4,
                    CARD_W,
                    CARD_H,
                    CORNER_RADIUS,
                    CORNER_RADIUS);
                g2.fill(r);
            }
            g2.setComposite(AlphaComposite.SrcOver);
        }

        private void paintHead(Graphics2D g2, int x, int y) {
            // Soft accent glow behind the avatar
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f * fade));
            g2.setColor(ACCENT);
            int glow = 10;
            g2.fill(new RoundRectangle2D.Float(x - glow, y - glow, HEAD_SIZE + glow * 2.0f, HEAD_SIZE + glow * 2.0f, 30, 30));
            g2.setComposite(AlphaComposite.SrcOver);

            RoundRectangle2D clip = new RoundRectangle2D.Float(x, y, HEAD_SIZE, HEAD_SIZE, 24, 24);
            BufferedImage img = head.get();
            g2.setClip(clip);
            if (img != null) {
                g2.drawImage(img, x, y, HEAD_SIZE, HEAD_SIZE, null);
            } else {
                g2.setColor(new Color(0x1A1A28));
                g2.fill(clip);
                Font mono = displayFont != null ? displayFont.deriveFont(Font.BOLD, 40f) : new Font(Font.SANS_SERIF, Font.BOLD, 40);
                g2.setFont(mono);
                g2.setColor(ACCENT);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f * fade));
                g2.drawString("OX", x + HEAD_SIZE / 2f - g2.getFontMetrics(mono).stringWidth("OX") / 2f,
                    y + HEAD_SIZE / 2f + g2.getFontMetrics(mono).getAscent() / 2f - 4);
            }
            g2.setClip(null);
            g2.setComposite(AlphaComposite.SrcOver);

            g2.setColor(new Color(255, 255, 255, 40));
            g2.draw(clip);
        }

        private void drawSpaced(Graphics2D g2, String text, float size, float centerX, float baselineY, float tracking, Color color) {
            Font font = displayFont != null ? displayFont.deriveFont(Font.PLAIN, size) : new Font(Font.SANS_SERIF, Font.PLAIN, (int) size);
            g2.setFont(font);
            float total = 0;
            for (int i = 0; i < text.length(); i++) {
                total += g2.getFontMetrics(font).charWidth(text.charAt(i)) + tracking;
            }
            total -= tracking;
            float x = centerX - total / 2f;
            g2.setColor(color);
            for (int i = 0; i < text.length(); i++) {
                g2.drawString(String.valueOf(text.charAt(i)), x, baselineY);
                x += g2.getFontMetrics(font).charWidth(text.charAt(i)) + tracking;
            }
        }
    }
}