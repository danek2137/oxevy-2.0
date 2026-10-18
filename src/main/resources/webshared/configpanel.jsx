/* ── Oxevy ConfigPanel (shared) ────────────────────────────────────────────
   The config manager UI: a Load grid of Roblox-style tiles with a "+"
   tile that expands in place to create/import/redeem a config. Loaded by
   BOTH the main menu overlay and the ClickGui "Configs" tab, so one
   component serves both hosts.

   No module system: both hosts pull this in through Babel's
   <script type="text/babel"> tag, so it just assigns window.ConfigPanelLib.
   The host document must provide React, ReactDOM and the cfg-* stylesheet. */
(function () {
  "use strict";
  const { memo, useState, useCallback, useEffect } = React;

  // ── base64url helpers for config share codes ───────────────────────────
  const b64urlEnc = (buf) => {
    const bytes = new Uint8Array(buf);
    let s = "";
    for (let i = 0; i < bytes.length; i += 8192)
      s += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
    return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  };
  const b64urlDec = (str) => {
    const s = String(str).replace(/-/g, "+").replace(/_/g, "/");
    const bin = atob(s + "=".repeat((4 - (s.length % 4)) % 4));
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes;
  };

const ConfigPanel = memo(function ConfigPanel({ onClose }) {
  const [configs, setConfigs] = useState([]);
  const [current, setCurrent] = useState("");
  const [creating, setCreating] = useState(false); // "+" tile expanded
  const [name, setName] = useState("");
  const [msg, setMsg] = useState("");
  const [exportData, setExportData] = useState(null); // {name, text, code}
  const [redeemCode, setRedeemCode] = useState("");
  const [codeCopied, setCodeCopied] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const r = await fetch("/api/config/list", { cache: "no-store" });
      if (r.ok) { const d = await r.json(); setConfigs(d.configs || []); setCurrent(d.current || ""); }
    } catch (e) {}
  }, []);
  useEffect(() => { refresh(); }, [refresh]);

  const post = async (url, payload) => {
    try {
      const r = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      return r.ok ? await r.json() : null;
    } catch (e) { return null; }
  };

  // Save current module state under `n` (blank = the name in the input).
  const saveAs = async (n) => {
    const target = String(n || name || "").trim();
    if (!target) { setMsg("Enter a name"); return; }
    const d = await post("/api/config/save", { name: target });
    if (d && d.ok) { setMsg("Saved " + target); setName(""); setCreating(false); refresh(); }
    else setMsg((d && d.error) || "Save failed");
  };
  const load = async (n) => {
    const d = await post("/api/config/load", { name: n });
    if (d && d.ok) { setMsg("Loaded " + n); refresh(); }
    else setMsg((d && d.error) || "Load failed");
  };
  const del = async (n) => {
    if (!confirm("Delete config " + n + "?")) return;
    const d = await post("/api/config/delete", { name: n });
    if (d && d.ok) { setMsg("Deleted " + n); refresh(); }
    else setMsg((d && d.error) || "Delete failed");
  };
  const exportCfg = async (n) => {
    const d = await post("/api/config/export", { name: n });
    if (d && d.ok && d.bundle) {
      const text = JSON.stringify(d.bundle, null, 2);
      setCodeCopied(false);
      setExportData({ name: n, text: text, code: "" });
      // Pack it into a share code (deflate + base64url); shows once ready.
      try { const code = await shareCodeOf(JSON.stringify(d.bundle)); setExportData({ name: n, text: text, code: code }); }
      catch (e) {}
    }
    else setMsg((d && d.error) || "Export failed");
  };
  const downloadExport = () => {
    if (!exportData) return;
    try {
      const blob = new Blob([exportData.text], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = exportData.name + ".oxevy.json";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
    } catch (e) { setMsg("Download failed"); }
  };
  const copyExport = async () => {
    if (!exportData) return;
    try {
      await navigator.clipboard.writeText(exportData.text);
      setMsg("Copied " + exportData.name + " to clipboard");
    } catch (e) {
      const ta = document.getElementById("cfg-export-text");
      if (ta) { ta.focus(); ta.select(); }
      setMsg("Selected - press Ctrl+C");
    }
  };
  const copyCode = async () => {
    if (!exportData || !exportData.code) return;
    try {
      await navigator.clipboard.writeText(exportData.code);
      setCodeCopied(true);
      setTimeout(() => setCodeCopied(false), 1600);
    } catch (e) {
      const el = document.getElementById("cfg-code-text");
      if (el) { el.focus(); el.select(); }
    }
  };
  const importFile = async (e) => {
    const input = e.currentTarget;
    const f = input.files && input.files[0];
    input.value = "";
    if (!f) return;
    let bundle = null;
    try { bundle = JSON.parse(await f.text()); } catch (err) { setMsg("Not a valid JSON file"); return; }
    let target = (bundle && typeof bundle.name === "string" && bundle.name) || f.name.replace(/\.[^.]+$/, "");
    target = String(target).toLowerCase().replace(/[^a-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "");
    if (!target) { setMsg("Cannot derive a config name"); return; }
    const d = await post("/api/config/import", { name: target, bundle: bundle });
    if (d && d.ok) { setMsg("Imported " + target); setCreating(false); refresh(); }
    else setMsg((d && d.error) || "Import failed");
  };

  // ── Share codes ────────────────────────────────────────────────────────────
  // A share code is the export bundle packed into one paste-able string:
  // OXEVY1D:<base64url(deflate-raw(bundle JSON))> (compressed)
  // OXEVY1R:<base64url(bundle JSON)> (raw fallback)
  // No server needed - the code carries the whole config, so friends can
  // redeem it even on a different machine.
  const shareCodeOf = async (text) => {
    try {
      if (typeof CompressionStream !== "undefined") {
        const stream = new Blob([text]).stream().pipeThrough(new CompressionStream("deflate-raw"));
        return "OXEVY1D:" + b64urlEnc(await new Response(stream).arrayBuffer());
      }
    } catch (e) {}
    return "OXEVY1R:" + b64urlEnc(new TextEncoder().encode(text).buffer);
  };
  const parseShareCode = async (code) => {
    const m = String(code).replace(/\s+/g, "").match(/^OXEVY1([DR]):(.+)$/i);
    if (!m) return null;
    const bytes = b64urlDec(m[2]);
    if (m[1].toUpperCase() === "D") {
      if (typeof DecompressionStream === "undefined") return null;
      const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
      return await new Response(stream).text();
    }
    return new TextDecoder().decode(bytes);
  };
  const redeem = async () => {
    const c = redeemCode.trim();
    if (!c) { setMsg("Paste a share code first"); return; }
    let text = null;
    try { text = await parseShareCode(c); } catch (e) {}
    if (!text) { setMsg("Not a valid share code"); return; }
    let bundle = null;
    try { bundle = JSON.parse(text); } catch (e) {}
    if (!bundle || typeof bundle !== "object" || !bundle.files) { setMsg("Share code is corrupt"); return; }
    let target = (typeof bundle.name === "string" && bundle.name) || "imported";
    target = String(target).toLowerCase().replace(/[^a-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "") || "imported";
    const d = await post("/api/config/import", { name: target, bundle: bundle });
    if (d && d.ok) { setMsg("Imported " + target + " from code"); setRedeemCode(""); setCreating(false); refresh(); }
    else setMsg((d && d.error) || "Import failed");
  };

  // Deterministic per-name cover gradient, so every tile gets its own color.
  const coverTint = (n) => {
    const s = String(n || "new");
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return "linear-gradient(145deg,hsl(" + h + " 55% 34%),hsl(" + ((h + 42) % 360) + " 60% 17%))";
  };
  const msgIsErr = !!(msg && /^(Enter|Cannot|.*(failed|error|not found|not a valid|corrupt|invalid|Selected))/i.test(msg));
  const glyph = (n) => {
    const c = String(n || "N").trim().charAt(0);
    return (c || "N").toUpperCase();
  };

  return (
    <div className="cfg-win">
      <div className="cfg-header">
        <div className="cfg-title">
          <h1>Configs</h1>
          <span className="cfg-count">{configs.length}</span>
        </div>
        <button className="cfg-close" onClick={onClose} title="Close">&#10005;</button>
      </div>

      <div className="cfg-shell">
        <nav className="cfg-rail">
          <button className="cfg-tab on" onClick={() => { setCreating(false); setMsg(""); }} title="Your saved configs">
            <svg width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
              <rect x="2" y="2" width="5" height="5" rx="1.6" />
              <rect x="9" y="2" width="5" height="5" rx="1.6" />
              <rect x="2" y="9" width="5" height="5" rx="1.6" />
              <rect x="9" y="9" width="5" height="5" rx="1.6" />
            </svg>
            <span>Load</span>
          </button>
        </nav>

        <div className="cfg-body">
          <div className="cfg-grid">
            <div className={"cfg-msg" + (msgIsErr ? " err" : "")} style={{ gridColumn: "1 / -1" }}>{msg ? msg : "\u00a0"}</div>
            {configs.map((c) => (
              <div key={c} className={"cfg-card" + (c === current ? " on" : "")}>
                <div className="cfg-cover" style={{ backgroundImage: coverTint(c) }}>
                  <span className="cfg-glyph">{glyph(c)}</span>
                  {c === current ? <span className="cfg-badge">ACTIVE</span> : null}
                  <div className="cfg-cover-acts">
                    <button className="cfg-btn primary" onClick={() => load(c)} disabled={c === current} title="Apply this config">Load</button>
                    <button className="cfg-btn" onClick={() => saveAs(c)} title="Overwrite it with your current settings">Modify</button>
                    <button className="cfg-btn" onClick={() => exportCfg(c)} title="Export as a file or share code">Export</button>
                    <button className="cfg-btn danger" onClick={() => del(c)} disabled={c === "default"} title="Delete it">Delete</button>
                  </div>
                </div>
                <div className="cfg-card-meta">
                  <span className="cfg-card-name">{c}</span>
                  <span className="cfg-card-sub">{c === current ? "In use" : c === "default" ? "Profile" : "Saved"}</span>
                </div>
              </div>
            ))}
            {creating ? (
              <div className="cfg-card cfg-new cfg-new-open">
                <button className="cfg-close cfg-new-cancel" onClick={() => { setCreating(false); setMsg(""); }} title="Cancel">&#10005;</button>
                <div className="cfg-create">
                  <div className="cfg-add">
                    <input
                      className="cfg-input"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      onKeyDown={(e) => { if (e.key === "Enter") saveAs(); }}
                      placeholder="config name"
                      autoFocus
                    />
                    <button className="cfg-btn primary" onClick={() => saveAs()}>Create</button>
                  </div>
                  <label className="cfg-import">
                    or import a config file (.json)
                    <input type="file" accept=".json,application/json" onChange={importFile} hidden />
                  </label>
                  <div className="cfg-or">or redeem a friend's share code</div>
                  <div className="cfg-redeem">
                    <input
                      className="cfg-input"
                      value={redeemCode}
                      onChange={(e) => setRedeemCode(e.target.value)}
                      onKeyDown={(e) => { if (e.key === "Enter") redeem(); }}
                      placeholder="OXEVY1D:..."
                      spellCheck={false}
                    />
                    <button className="cfg-btn primary" onClick={() => redeem()}>Redeem</button>
                  </div>
                </div>
              </div>
            ) : (
              <div
                className="cfg-card cfg-new"
                role="button"
                tabIndex={0}
                onClick={() => { setCreating(true); setMsg(""); }}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setCreating(true); setMsg(""); } }}
                title="Create a config"
              >
                <div className="cfg-new-area"><span className="cfg-new-plus">+</span></div>
                <div className="cfg-card-meta">
                  <span className="cfg-card-name">New config</span>
                  <span className="cfg-card-sub">Create</span>
                </div>
              </div>
            )}
            {configs.length === 0 ? (
              <div className="cfg-empty">No configs yet tap the + to create one.</div>
            ) : null}
          </div>
        </div>
      </div>

      {exportData ? (
        <div className="cfg-export">
          <div className="cfg-export-head">
            <span className="cfg-export-title">Export {exportData.name}</span>
            <button className="cfg-close" onClick={() => setExportData(null)} title="Close">&#10005;</button>
          </div>
          <textarea id="cfg-export-text" readOnly value={exportData.text} onFocus={(e) => e.currentTarget.select()} />
          <div className="cfg-export-row">
            <button className="cfg-btn" onClick={copyExport}>Copy</button>
            <button className="cfg-btn primary" onClick={downloadExport}>Download</button>
          </div>
          <div className="cfg-or">or share it with a code</div>
          <div className="cfg-export-code">
            <input
              id="cfg-code-text"
              className="cfg-input"
              readOnly
              spellCheck={false}
              value={exportData.code || "generating\u2026"}
              onFocus={(e) => e.currentTarget.select()}
              title="Paste this code where a friend can redeem it (the + tile)"
            />
            <button className="cfg-btn primary" onClick={copyCode} disabled={!exportData.code} title="Copy the share code">{codeCopied ? "Copied!" : "Copy code"}</button>
          </div>
        </div>
      ) : null}
    </div>
  );
});

  window.ConfigPanelLib = { ConfigPanel: ConfigPanel };
})();
