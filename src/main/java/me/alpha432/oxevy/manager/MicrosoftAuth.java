package me.alpha432.oxevy.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Full Microsoft -> Xbox Live -> XSTS -> Minecraft authentication chain.
 * Uses the device code flow so no browser redirect is needed.
 */
public final class MicrosoftAuth {

    /** Azure application client ID (public, device-code capable). */
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE     = "XboxLive.signin offline_access";

    private MicrosoftAuth() {}

    // ── Public data classes ───────────────────────────────────────────

    public static class DeviceCodeResponse {
        public final String userCode;
        public final String deviceCode;
        public final String verificationUri;
        public final int    expiresIn;
        public final int    interval;

        DeviceCodeResponse(String userCode, String deviceCode,
                           String verificationUri, int expiresIn, int interval) {
            this.userCode        = userCode;
            this.deviceCode      = deviceCode;
            this.verificationUri = verificationUri;
            this.expiresIn       = expiresIn;
            this.interval        = interval;
        }
    }

    public static class AuthResult {
        public final boolean success;
        public final String  username;
        public final UUID    uuid;
        public final String  accessToken;
        public final String  refreshToken;
        public final String  error;

        // success
        AuthResult(String username, UUID uuid, String accessToken, String refreshToken) {
            this.success      = true;
            this.username     = username;
            this.uuid         = uuid;
            this.accessToken  = accessToken;
            this.refreshToken = refreshToken;
            this.error        = null;
        }

        // failure
        AuthResult(String error) {
            this.success      = false;
            this.username     = null;
            this.uuid         = null;
            this.accessToken  = null;
            this.refreshToken = null;
            this.error        = error;
        }
    }

    // ── Step 1 – Request device code ─────────────────────────────────

    public static DeviceCodeResponse requestDeviceCode() throws Exception {
        String body = "client_id=" + enc(CLIENT_ID) + "&scope=" + enc(SCOPE);
        String res  = post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                           body, "application/x-www-form-urlencoded");
        JsonObject json = parse(res);
        return new DeviceCodeResponse(
                json.get("user_code").getAsString(),
                json.get("device_code").getAsString(),
                json.get("verification_uri").getAsString(),
                json.get("expires_in").getAsInt(),
                json.get("interval").getAsInt()
        );
    }

    // ── Step 2 – Poll until the user completes browser login ──────────

    /**
     * Spawns a daemon thread that polls until the user finishes authenticating,
     * then calls {@code onDone}. The caller must schedule UI updates themselves.
     */
    public static Thread pollForToken(String deviceCode, int intervalSec, int expiresIn,
                                      Consumer<AuthResult> onDone) {
        Thread t = new Thread(() -> {
            long deadline = System.currentTimeMillis() + (long) expiresIn * 1000L;
            while (System.currentTimeMillis() < deadline) {
                try { Thread.sleep(intervalSec * 1000L); } catch (InterruptedException e) { return; }
                try {
                    AuthResult r = pollOnce(deviceCode);
                    if (r != null) { onDone.accept(r); return; }
                } catch (Exception ignored) {}
            }
            onDone.accept(new AuthResult("Login timed out"));
        }, "msa-poll");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** @return null if still pending, AuthResult otherwise */
    private static AuthResult pollOnce(String deviceCode) throws Exception {
        String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                + "&client_id=" + enc(CLIENT_ID)
                + "&device_code=" + enc(deviceCode);
        String res = post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                          body, "application/x-www-form-urlencoded");
        JsonObject json = parse(res);
        if (json.has("error")) {
            String err = json.get("error").getAsString();
            if ("authorization_pending".equals(err) || "slow_down".equals(err)) return null;
            return new AuthResult(err);
        }
        return msaToMinecraft(
                json.get("access_token").getAsString(),
                json.has("refresh_token") ? json.get("refresh_token").getAsString() : ""
        );
    }

    // ── Refresh (re-authenticate an existing stored account) ──────────

    public static AuthResult refreshToken(String refreshToken) {
        try {
            String body = "grant_type=refresh_token"
                    + "&client_id=" + enc(CLIENT_ID)
                    + "&refresh_token=" + enc(refreshToken)
                    + "&scope=" + enc(SCOPE);
            String res = post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                              body, "application/x-www-form-urlencoded");
            JsonObject json = parse(res);
            if (json.has("error")) return new AuthResult(json.get("error").getAsString());
            String newRefresh = json.has("refresh_token")
                    ? json.get("refresh_token").getAsString() : refreshToken;
            return msaToMinecraft(json.get("access_token").getAsString(), newRefresh);
        } catch (Exception e) {
            return new AuthResult(e.getMessage());
        }
    }

    // ── Internal auth chain ───────────────────────────────────────────

    private static AuthResult msaToMinecraft(String msaToken, String msaRefresh) throws Exception {
        // Xbox Live
        String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + msaToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        JsonObject xbl = parse(postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody));
        String xblToken = xbl.get("Token").getAsString();
        String uhs      = xbl.get("DisplayClaims").getAsJsonObject()
                             .get("xui").getAsJsonArray()
                             .get(0).getAsJsonObject()
                             .get("uhs").getAsString();

        // XSTS
        String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + xblToken + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        JsonObject xsts = parse(postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody));
        if (xsts.has("XErr")) {
            long xErr = xsts.get("XErr").getAsLong();
            if (xErr == 2148916238L) return new AuthResult("Account not in an Xbox Live region");
            if (xErr == 2148916233L) return new AuthResult("No Xbox account linked to this Microsoft account");
            return new AuthResult("XSTS error: " + xErr);
        }
        String xstsToken = xsts.get("Token").getAsString();

        // Minecraft login
        String mcBody = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}";
        JsonObject mc  = parse(postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody));
        String mcToken = mc.get("access_token").getAsString();

        // Minecraft profile
        JsonObject profile = parse(getAuth("https://api.minecraftservices.com/minecraft/profile", mcToken));
        if (profile.has("error")) return new AuthResult("Account does not own Minecraft");

        String username = profile.get("name").getAsString();
        String rawUuid  = profile.get("id").getAsString();
        UUID uuid = UUID.fromString(rawUuid.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        return new AuthResult(username, uuid, mcToken, msaRefresh);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────

    private static String post(String url, String body, String contentType) throws Exception {
        HttpURLConnection c = open(url, "POST");
        c.setRequestProperty("Content-Type", contentType);
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        return read(c);
    }

    private static String postJson(String url, String body) throws Exception {
        HttpURLConnection c = open(url, "POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        return read(c);
    }

    private static String getAuth(String url, String token) throws Exception {
        HttpURLConnection c = open(url, "GET");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        return read(c);
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        return c;
    }

    private static String read(HttpURLConnection c) throws Exception {
        InputStream is = null;
        try { is = c.getInputStream(); } catch (Exception e) { is = c.getErrorStream(); }
        if (is == null) return "{}";
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
