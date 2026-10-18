# Oxevy

<div align="center">

**⚠️ THIS PROJECT IS IN BETA ⚠️**

**An AI-based utility client, OyVey-ported**

Built with: ChatGPT • DeepSeek • OpenCode • Gemini • Cursor

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-7.3.4-blue.svg)](https://github.com/danek2137/oxevy-2.0/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-loader-orange.svg)](https://fabricmc.net/)
[![MCEF](https://img.shields.io/badge/required-MCEF-red.svg)](https://modrinth.com/mod/mcef-modern)

</div>

## ⚠️ Requirements — MCEF is mandatory

Oxevy renders **all of its menus through the browser** (main menu, ClickGUI,
pause screen, accounts, cosmetics, config manager). That means:

| Requirement | Version |
|---|---|
| Java | 21+ |
| Minecraft | **1.21.11** |
| Fabric Loader | 0.19.x+ (with Fabric API) |
| **MCEF (`mcef-modern`)** | **`0.3.3+mc1.21.11.jcef146.0.10`** — [Modrinth](https://modrinth.com/mod/mcef-modern) |

**You need MCEF to run Oxevy.** Without `mcef-modern` installed the mod is
rejected by Fabric Loader (it is declared as a hard dependency), and without a
working JCEF the UI cannot render.

## Installation (players)

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11 +
   Fabric API.
2. Install **[MCEF (`mcef-modern`)](https://modrinth.com/mod/mcef-modern)** —
   the version above. It also needs a working JCEF (bundled with the mod on
   most platforms).
3. Drop `oxevy-7.3.4.jar` from the [latest release](https://github.com/danek2137/oxevy-2.0/releases)
   into your `mods` folder.
4. Launch the game — Oxevy opens its own main menu.

## Minecraft versions / branches

The project is split into one branch per supported Minecraft version:

| Branch | Minecraft | Status |
|---|---|---|
| [`main`](https://github.com/danek2137/oxevy-2.0) / [`1.21.11`](https://github.com/danek2137/oxevy-2.0/tree/1.21.11) | 1.21.11 | **Mainline — current development** (web UIs, MCEF, config share codes) |
| [`1.21.8`](https://github.com/danek2137/oxevy-2.0/tree/1.21.8) | 1.21.8 | **Outdated — not mainline.** Frozen snapshot, no MCEF/web-UI updates |
| [`1.21.4`](https://github.com/danek2137/oxevy-2.0/tree/1.21.4) | 1.21.4 | **Outdated — not mainline.** Frozen snapshot, no MCEF/web-UI updates |

Only **1.21.11** is mainline. The 1.21.8 / 1.21.4 branches are kept for
players on older versions and do not receive new features — MCEF-backed web
UIs are 1.21.11-only. Ready-made jars for every supported version are attached
to each [release](https://github.com/danek2137/oxevy-2.0/releases).

## Features

- **Web-based UI** — main menu, ClickGUI, pause screen, accounts and cosmetics
  screens rendered in-engine, transparent liquid-glass styling.
- **Config manager** — save / load / modify / delete named configs, with
  **share codes** (`OXEVY1D:…`) so you can send a whole config to a friend as
  a single string, or export/import `.json` files. The same panel opens from
  the main menu and from the ClickGUI's *Configs* tab.
- **Modules** — combat, movement, render, HUD, misc and client modules with
  keybinds, friends and profiles (built on the OyVey base).
- **Cosmetics** — capes, skins and head accessories, rendered on your player.

## Building (developers)

```bash
git clone https://github.com/danek2137/oxevy-2.0.git
cd oxevy-2.0
./gradlew build
# jar lands in build/libs/
```

Cross-version builds from a checkout (per-version profiles live in `versions/`):

```bash
./gradlew build -PmcVersion=1.21.8
./gradlew build -PmcVersion=1.21.4
```

`libs/` must contain the `mcef-modern` jar (used for compile-time API access
only — it is *not* nested into the output jar).

## Preview

### OyVey-ported
![Original UI](images/ui.png)

### Oxevy UI
![Old UI](images/ui1.png)
![New UI](images/ui2.png)

## License

[MIT](LICENSE)
