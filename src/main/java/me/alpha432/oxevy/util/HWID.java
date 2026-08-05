package me.alpha432.oxevy.util;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Objects;
import java.util.stream.Stream;

public final class HWID {

    private static volatile String cached;

    private HWID() {}

    public static String get() {
        if (cached == null) {
            synchronized (HWID.class) {
                if (cached == null) {
                    cached = compute();
                }
            }
        }
        return cached;
    }

    private static String compute() {
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("os.name", "")).append('|')
          .append(System.getProperty("os.version", "")).append('|')
          .append(System.getProperty("os.arch", "")).append('|');

        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint() || !ni.isUp()) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length < 6) continue;
                String name = ni.getName();
                if (name == null || name.startsWith("veth") || name.startsWith("docker")
                        || name.startsWith("br-") || name.startsWith("vmnet")) continue;
                for (byte b : mac) sb.append(String.format("%02x", b));
                sb.append('|');
            }
        } catch (Exception ignored) {
        }

        sb.append(readFirst("/sys/class/dmi/id/product_uuid")).append('|');
        sb.append(readFirst("/sys/class/dmi/id/board_serial")).append('|');
        sb.append(readFirst("/sys/class/dmi/id/product_serial")).append('|');
        sb.append(readFirst("/sys/class/dmi/id/motherboard")).append('|');

        try (Stream<Path> blocks = Files.list(Path.of("/sys/block"))) {
            blocks.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("sd") || n.startsWith("hd") || n.startsWith("nvme") || n.startsWith("mmc");
            }).forEach(p -> {
                try {
                    String serial = Files.readString(p.resolve("serial")).trim();
                    if (!serial.isEmpty()) sb.append(p.getFileName()).append(':').append(serial).append('|');
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }

        try {
            String cpuinfo = Files.readString(Path.of("/proc/cpuinfo"));
            String model = cpuinfo.lines()
                    .filter(l -> l.startsWith("model name"))
                    .findFirst()
                    .orElse("")
                    .replaceAll(".*:", "").trim();
            long cores = cpuinfo.lines().filter(l -> l.startsWith("processor")).count();
            sb.append(model).append('|').append(cores).append('|');
        } catch (Exception ignored) {
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(Objects.hashCode(sb.toString())).replace('-', '0');
        }
    }

    private static String readFirst(String path) {
        try {
            return Files.readString(Path.of(path)).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
