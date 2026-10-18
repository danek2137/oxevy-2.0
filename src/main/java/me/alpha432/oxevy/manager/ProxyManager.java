package me.alpha432.oxevy.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.util.traits.Jsonable;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProxyManager implements Jsonable {
    public static class ProxyInfo {
        public String name;
        public String ip;
        public int port;
        public boolean custom;
        public transient long ping = -1;
        public transient boolean pinging = false;

        public ProxyInfo(String name, String ip, int port, boolean custom) {
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.custom = custom;
        }
    }

    private final List<ProxyInfo> proxies = new ArrayList<>();
    private ProxyInfo activeProxy = null;

    public void init() {
        // ── United States ────────────────────────────────────────────────
        proxies.add(new ProxyInfo("US · New York 1",     "192.111.139.162", 4145, false));
        proxies.add(new ProxyInfo("US · New York 2",     "192.111.139.163", 4145, false));
        proxies.add(new ProxyInfo("US · Dallas 1",       "173.233.137.35",  4145, false));
        proxies.add(new ProxyInfo("US · Dallas 2",       "173.233.137.45",  4145, false));
        proxies.add(new ProxyInfo("US · Chicago 1",      "72.217.216.239",  46801, false));
        proxies.add(new ProxyInfo("US · Chicago 2",      "174.77.111.197",  4145, false));
        proxies.add(new ProxyInfo("US · Los Angeles 1",  "98.162.25.7",     4145, false));
        proxies.add(new ProxyInfo("US · Los Angeles 2",  "98.162.96.52",    4145, false));
        proxies.add(new ProxyInfo("US · Miami 1",        "184.178.172.13",  4145, false));
        proxies.add(new ProxyInfo("US · Miami 2",        "184.178.172.28",  15294, false));
        proxies.add(new ProxyInfo("US · Atlanta 1",      "70.166.167.36",   4145, false));
        proxies.add(new ProxyInfo("US · Atlanta 2",      "70.166.167.55",   57745, false));
        proxies.add(new ProxyInfo("US · Seattle 1",      "98.185.94.76",    4145, false));
        proxies.add(new ProxyInfo("US · Seattle 2",      "98.185.94.94",    4145, false));
        proxies.add(new ProxyInfo("US · Phoenix 1",      "67.201.33.9",     4145, false));

        // ── Canada ───────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("CA · Toronto 1",      "184.174.14.30",   4145, false));
        proxies.add(new ProxyInfo("CA · Toronto 2",      "184.174.14.58",   57742, false));
        proxies.add(new ProxyInfo("CA · Montreal 1",     "174.64.199.79",   4145, false));
        proxies.add(new ProxyInfo("CA · Vancouver 1",    "24.249.199.4",    4145, false));
        proxies.add(new ProxyInfo("CA · Vancouver 2",    "24.249.199.12",   4145, false));

        // ── United Kingdom ───────────────────────────────────────────────
        proxies.add(new ProxyInfo("UK · London 1",       "51.79.52.80",     3080, false));
        proxies.add(new ProxyInfo("UK · London 2",       "185.220.101.33",  10000, false));
        proxies.add(new ProxyInfo("UK · London 3",       "185.220.101.45",  10000, false));

        // ── Germany ──────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("DE · Frankfurt 1",    "98.162.25.23",    4145, false));
        proxies.add(new ProxyInfo("DE · Frankfurt 2",    "185.220.101.10",  10000, false));
        proxies.add(new ProxyInfo("DE · Frankfurt 3",    "185.220.101.15",  10000, false));
        proxies.add(new ProxyInfo("DE · Berlin 1",       "185.220.101.19",  10000, false));

        // ── Netherlands ──────────────────────────────────────────────────
        proxies.add(new ProxyInfo("NL · Amsterdam 1",    "185.220.101.20",  10000, false));
        proxies.add(new ProxyInfo("NL · Amsterdam 2",    "185.220.101.21",  10000, false));
        proxies.add(new ProxyInfo("NL · Amsterdam 3",    "51.158.54.208",   1080, false));

        // ── France ───────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("FR · Paris 1",        "51.158.166.24",   1080, false));
        proxies.add(new ProxyInfo("FR · Paris 2",        "51.158.166.64",   1080, false));

        // ── Sweden ───────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("SE · Stockholm 1",    "185.220.101.24",  10000, false));
        proxies.add(new ProxyInfo("SE · Stockholm 2",    "185.220.101.26",  10000, false));

        // ── Russia ───────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("RU · Moscow 1",       "185.179.196.18",  1080, false));
        proxies.add(new ProxyInfo("RU · Moscow 2",       "195.201.120.229", 1080, false));
        proxies.add(new ProxyInfo("RU · St. Petersburg", "62.33.207.201",   1080, false));

        // ── Hong Kong ────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("HK · Hong Kong 1",   "120.241.90.65",   1080, false));
        proxies.add(new ProxyInfo("HK · Hong Kong 2",   "103.216.145.10",  1080, false));
        proxies.add(new ProxyInfo("HK · Hong Kong 3",   "103.216.145.11",  1080, false));

        // ── Japan ────────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("JP · Tokyo 1",        "150.95.145.159",  1080, false));
        proxies.add(new ProxyInfo("JP · Tokyo 2",        "150.95.145.160",  1080, false));
        proxies.add(new ProxyInfo("JP · Osaka 1",        "47.245.56.108",   1080, false));

        // ── Singapore ────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("SG · Singapore 1",   "8.213.128.90",    1080, false));
        proxies.add(new ProxyInfo("SG · Singapore 2",   "8.213.128.91",    1080, false));
        proxies.add(new ProxyInfo("SG · Singapore 3",   "47.245.120.12",   1080, false));

        // ── South Korea ──────────────────────────────────────────────────
        proxies.add(new ProxyInfo("KR · Seoul 1",        "211.62.11.173",   1080, false));
        proxies.add(new ProxyInfo("KR · Seoul 2",        "175.207.2.71",    1080, false));

        // ── Brazil ───────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("BR · São Paulo 1",    "177.234.249.187", 4145, false));
        proxies.add(new ProxyInfo("BR · São Paulo 2",    "177.234.249.188", 57690, false));

        // ── Australia ────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("AU · Sydney 1",       "43.134.120.74",   1080, false));
        proxies.add(new ProxyInfo("AU · Sydney 2",       "43.134.120.77",   1080, false));

        // ── India ────────────────────────────────────────────────────────
        proxies.add(new ProxyInfo("IN · Mumbai 1",       "43.153.110.42",   1080, false));
        proxies.add(new ProxyInfo("IN · Mumbai 2",       "43.153.110.45",   1080, false));

        Oxevy.configManager.addConfig(this);
    }

    public List<ProxyInfo> getProxies() {
        return proxies;
    }

    public ProxyInfo getActiveProxy() {
        return activeProxy;
    }

    public void addProxy(String name, String ip, int port) {
        if (ip == null || ip.trim().isEmpty() || port <= 0 || port > 65535) return;
        proxies.add(new ProxyInfo(name, ip, port, true));
        Oxevy.configManager.save();
    }

    public void removeProxy(ProxyInfo proxy) {
        if (proxy == null || !proxy.custom) return;
        if (activeProxy == proxy) {
            useProxy(null);
        }
        proxies.remove(proxy);
        Oxevy.configManager.save();
    }

    public void useProxy(ProxyInfo proxy) {
        if (proxy == null) {
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
            activeProxy = null;
        } else {
            System.setProperty("socksProxyHost", proxy.ip);
            System.setProperty("socksProxyPort", String.valueOf(proxy.port));
            activeProxy = proxy;
        }
    }

    public void pingProxy(ProxyInfo proxy) {
        if (proxy.pinging) return;
        proxy.pinging = true;
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(proxy.ip, proxy.port), 1500);
                proxy.ping = System.currentTimeMillis() - start;
            } catch (Exception e) {
                proxy.ping = -2; // Offline/Timeout
            } finally {
                proxy.pinging = false;
            }
        });
    }

    public void pingAll() {
        for (ProxyInfo proxy : proxies) {
            pingProxy(proxy);
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (ProxyInfo p : proxies) {
            if (p.custom) {
                JsonObject po = new JsonObject();
                po.addProperty("name", p.name);
                po.addProperty("ip", p.ip);
                po.addProperty("port", p.port);
                array.add(po);
            }
        }
        object.add("proxies", array);
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        // Clear only custom proxies
        proxies.removeIf(p -> p.custom);
        if (element != null && element.isJsonObject() && element.getAsJsonObject().has("proxies")) {
            for (JsonElement e : element.getAsJsonObject().get("proxies").getAsJsonArray()) {
                JsonObject po = e.getAsJsonObject();
                String name = po.get("name").getAsString();
                String ip = po.get("ip").getAsString();
                int port = po.get("port").getAsInt();
                proxies.add(new ProxyInfo(name, ip, port, true));
            }
        }
    }

    @Override
    public String getFileName() {
        return "proxies.json";
    }
}
