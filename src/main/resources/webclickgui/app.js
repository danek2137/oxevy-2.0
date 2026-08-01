const state = {
  data: null,
  activeCategory: null,
  selectedModuleId: null,
  search: "",
  captureBind: null,
  lastSync: "Never",
};

const app = document.getElementById("app");

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(await response.text());
  }

  return response.json();
}

async function loadState() {
  const next = await api("/api/state");
  state.data = next;
  if (!state.activeCategory && next.categories.length > 0) {
    state.activeCategory = next.categories[0].key;
  }

  const activeCategory = getActiveCategory();
  const selectedModule = getSelectedModule();
  if (!activeCategory && next.categories.length > 0) {
    state.activeCategory = next.categories[0].key;
  }
  if (!selectedModule) {
    state.selectedModuleId = null;
  }

  state.lastSync = new Date().toLocaleTimeString();
  render();
}

function getActiveCategory() {
  return state.data?.categories.find((category) => category.key === state.activeCategory) ?? null;
}

function getFilteredModules() {
  const category = getActiveCategory();
  if (!category) {
    return [];
  }

  const needle = state.search.trim().toLowerCase();
  if (!needle) {
    return category.modules;
  }

  return category.modules.filter((module) =>
    module.name.toLowerCase().includes(needle) ||
    (module.description && module.description.toLowerCase().includes(needle))
  );
}

function getSelectedModule() {
  if (!state.data || !state.selectedModuleId) {
    return null;
  }

  for (const category of state.data.categories) {
    const module = category.modules.find((entry) => entry.id === state.selectedModuleId);
    if (module) {
      return module;
    }
  }

  return null;
}

function renderSidebar() {
  return `
    <aside class="panel sidebar">
      <div class="brand">
        <div class="brand-mark">OX</div>
        <small>${escapeHtml(state.data.name)} ${escapeHtml(state.data.version)}</small>
      </div>
      <div class="sidebar-list">
        ${state.data.categories.map((category) => `
          <button
            class="sidebar-item ${category.key === state.activeCategory ? "active" : ""}"
            data-action="select-category"
            data-category="${escapeHtml(category.key)}"
            title="${escapeHtml(category.name)}"
          >
            ${escapeHtml(category.name.slice(0, 2).toUpperCase())}
            ${category.enabledCount > 0 ? `<span class="sidebar-count">${category.enabledCount > 9 ? "9+" : category.enabledCount}</span>` : ""}
          </button>
        `).join("")}
      </div>
      <div class="sidebar-footer">
        <strong>${state.data.activeCount}</strong>
        <span>ACTIVE</span>
      </div>
    </aside>
  `;
}

function renderModules() {
  const category = getActiveCategory();
  const modules = getFilteredModules();
  const enabledCount = category ? category.modules.filter((entry) => entry.enabled).length : 0;

  return `
    <section class="panel main-panel">
      <div class="panel-header">
        <div class="panel-title">
          <h1>${escapeHtml(category?.name ?? "Modules")}</h1>
          <p>${enabledCount} / ${category?.modules.length ?? 0} enabled</p>
        </div>
        <div class="type-chip">${escapeHtml(state.data.url)}</div>
      </div>
      <div class="search-wrap">
        <input
          class="search-input"
          type="text"
          placeholder="Search modules"
          value="${escapeHtml(state.search)}"
          data-role="search"
        />
      </div>
      <div class="module-list">
        ${modules.length === 0 ? `<div class="setting-row"><span>No modules match the current filter.</span></div>` : modules.map((module) => `
          <div class="module-row ${module.id === state.selectedModuleId ? "selected" : ""}">
            <button
              class="module-toggle ${module.enabled ? "enabled" : ""}"
              data-action="toggle-module"
              data-module="${escapeHtml(module.id)}"
              title="${module.enabled ? "Disable" : "Enable"}"
            ></button>
            <button
              class="module-meta"
              data-action="select-module"
              data-module="${escapeHtml(module.id)}"
            >
              <strong>${escapeHtml(module.name)}</strong>
              <span>${escapeHtml(module.description)}</span>
            </button>
            <span class="keybind-chip">${escapeHtml(module.bind.label)}</span>
            <button
              class="action-button"
              data-action="select-module"
              data-module="${escapeHtml(module.id)}"
            >
              ${module.id === state.selectedModuleId ? "Hide" : "Open"}
            </button>
          </div>
        `).join("")}
      </div>
      <div class="status-bar">Last sync ${escapeHtml(state.lastSync)}</div>
    </section>
  `;
}

function renderBooleanSetting(module, setting) {
  return `
    <div class="setting-row">
      <div class="setting-head">
        <strong>${escapeHtml(setting.name)}</strong>
        <button
          class="inline-switch ${setting.value ? "enabled" : ""}"
          data-action="toggle-setting"
          data-module="${escapeHtml(module.id)}"
          data-setting="${escapeHtml(setting.id)}"
          data-value="${setting.value ? "false" : "true"}"
        ></button>
      </div>
    </div>
  `;
}

function renderNumberSetting(module, setting) {
  return `
    <div class="setting-row">
      <div class="setting-head">
        <strong>${escapeHtml(setting.name)}</strong>
        <span>${Number(setting.value).toFixed(setting.step === 1 ? 0 : 1)}</span>
      </div>
      <input
        type="range"
        min="${setting.min}"
        max="${setting.max}"
        step="${setting.step}"
        value="${setting.value}"
        data-action="range-setting"
        data-module="${escapeHtml(module.id)}"
        data-setting="${escapeHtml(setting.id)}"
      />
    </div>
  `;
}

function renderStringSetting(module, setting) {
  return `
    <div class="setting-row">
      <div class="setting-head">
        <strong>${escapeHtml(setting.name)}</strong>
      </div>
      <input
        type="text"
        value="${escapeHtml(setting.value)}"
        data-action="text-setting"
        data-module="${escapeHtml(module.id)}"
        data-setting="${escapeHtml(setting.id)}"
      />
    </div>
  `;
}

function renderEnumSetting(module, setting) {
  return `
    <div class="setting-row">
      <div class="setting-head">
        <strong>${escapeHtml(setting.name)}</strong>
      </div>
      <select
        data-action="select-setting"
        data-module="${escapeHtml(module.id)}"
        data-setting="${escapeHtml(setting.id)}"
      >
        ${setting.options.map((option) => `
          <option value="${escapeHtml(option)}" ${option === setting.value ? "selected" : ""}>
            ${escapeHtml(option)}
          </option>
        `).join("")}
      </select>
    </div>
  `;
}

function renderColorSetting(module, setting) {
  const color = setting.value;
  const rows = [
    ["r", "#ff5a5a"],
    ["g", "#5aff96"],
    ["b", "#5aaaff"],
    ["a", "#dcdcdc"],
  ];

  return `
    <div class="setting-row">
      <div class="setting-head">
        <strong>${escapeHtml(setting.name)}</strong>
        <span>rgba(${color.r}, ${color.g}, ${color.b}, ${color.a})</span>
      </div>
      ${rows.map(([channel, swatch]) => `
        <div class="color-grid">
          <span class="swatch" style="background:${swatch}"></span>
          <input
            type="range"
            min="0"
            max="255"
            step="1"
            value="${color[channel]}"
            data-action="color-setting"
            data-module="${escapeHtml(module.id)}"
            data-setting="${escapeHtml(setting.id)}"
            data-channel="${channel}"
          />
          <span>${color[channel]}</span>
        </div>
      `).join("")}
    </div>
  `;
}

function renderBindSetting(module, setting) {
  const capture = state.captureBind &&
    state.captureBind.moduleId === module.id &&
    state.captureBind.settingId === setting.id;

  return `
    <div class="setting-row">
      <div class="setting-head">
        <strong>${escapeHtml(setting.name)}</strong>
        <span>${escapeHtml(setting.value.label)}</span>
      </div>
      <div class="drawer-actions">
        <button
          class="bind-button"
          data-action="capture-bind"
          data-module="${escapeHtml(module.id)}"
          data-setting="${escapeHtml(setting.id)}"
        >
          ${capture ? "Press key..." : "Set key"}
        </button>
        <button
          class="action-button"
          data-action="reset-bind"
          data-module="${escapeHtml(module.id)}"
          data-setting="${escapeHtml(setting.id)}"
        >
          Clear
        </button>
      </div>
    </div>
  `;
}

function renderSetting(module, setting) {
  switch (setting.type) {
    case "boolean":
      return renderBooleanSetting(module, setting);
    case "number":
      return renderNumberSetting(module, setting);
    case "string":
      return renderStringSetting(module, setting);
    case "enum":
      return renderEnumSetting(module, setting);
    case "color":
      return renderColorSetting(module, setting);
    case "bind":
      return renderBindSetting(module, setting);
    default:
      return `
        <div class="setting-row">
          <div class="setting-head">
            <strong>${escapeHtml(setting.name)}</strong>
            <span>Unsupported</span>
          </div>
        </div>
      `;
  }
}

function renderDrawer() {
  const module = getSelectedModule();
  if (!module) {
    return `
      <aside class="panel drawer">
        <div class="drawer-empty">Select a module to inspect settings.</div>
      </aside>
    `;
  }

  return `
    <aside class="panel drawer">
      <div class="panel-header">
        <div class="panel-title">
          <h2>${escapeHtml(module.name)}</h2>
          <p>${escapeHtml(module.description)}</p>
        </div>
        <button class="action-button" data-action="close-drawer">Close</button>
      </div>
      <div class="drawer-body">
        <div class="setting-row">
          <div class="setting-head">
            <strong>Status</strong>
            <span>${module.enabled ? "Enabled" : "Disabled"}</span>
          </div>
          <div class="drawer-actions">
            <button
              class="action-button primary"
              data-action="toggle-module"
              data-module="${escapeHtml(module.id)}"
            >
              ${module.enabled ? "Disable" : "Enable"}
            </button>
          </div>
        </div>
        ${module.settings.map((setting) => renderSetting(module, setting)).join("")}
      </div>
    </aside>
  `;
}

function render() {
  if (!state.data) {
    app.innerHTML = `<div class="panel drawer-empty">Connecting to Web ClickGui...</div>`;
    return;
  }

  app.innerHTML = `
    <div class="shell">
      ${renderSidebar()}
      ${renderModules()}
      ${renderDrawer()}
    </div>
  `;

  wireEvents();
}

async function postJson(path, payload) {
  state.data = await api(path, {
    method: "POST",
    body: JSON.stringify(payload),
  });
  render();
}

function browserCodeToGlfw(code) {
  if (/^Key[A-Z]$/.test(code)) {
    return code.charCodeAt(3);
  }
  if (/^Digit[0-9]$/.test(code)) {
    return code.charCodeAt(5);
  }

  const map = {
    Space: 32,
    Escape: 256,
    Enter: 257,
    NumpadEnter: 257,
    Tab: 258,
    Backspace: 259,
    Insert: 260,
    Delete: 261,
    ArrowRight: 262,
    ArrowLeft: 263,
    ArrowDown: 264,
    ArrowUp: 265,
    PageUp: 266,
    PageDown: 267,
    Home: 268,
    End: 269,
    CapsLock: 280,
    ScrollLock: 281,
    NumLock: 282,
    PrintScreen: 283,
    Pause: 284,
    F1: 290,
    F2: 291,
    F3: 292,
    F4: 293,
    F5: 294,
    F6: 295,
    F7: 296,
    F8: 297,
    F9: 298,
    F10: 299,
    F11: 300,
    F12: 301,
    ShiftLeft: 340,
    ControlLeft: 341,
    AltLeft: 342,
    ShiftRight: 344,
    ControlRight: 345,
    AltRight: 346,
    Minus: 45,
    Equal: 61,
    BracketLeft: 91,
    BracketRight: 93,
    Backslash: 92,
    Semicolon: 59,
    Quote: 39,
    Comma: 44,
    Period: 46,
    Slash: 47,
    Backquote: 96,
  };

  return map[code] ?? null;
}

function wireEvents() {
  app.querySelectorAll("[data-action='select-category']").forEach((button) => {
    button.addEventListener("click", () => {
      state.activeCategory = button.dataset.category;
      state.selectedModuleId = null;
      render();
    });
  });

  app.querySelectorAll("[data-action='select-module']").forEach((button) => {
    button.addEventListener("click", () => {
      const moduleId = button.dataset.module;
      state.selectedModuleId = state.selectedModuleId === moduleId ? null : moduleId;
      render();
    });
  });

  app.querySelectorAll("[data-action='toggle-module']").forEach((button) => {
    button.addEventListener("click", async () => {
      await postJson("/api/module/toggle", { module: button.dataset.module });
    });
  });

  app.querySelectorAll("[data-action='toggle-setting']").forEach((button) => {
    button.addEventListener("click", async () => {
      await postJson("/api/setting", {
        module: button.dataset.module,
        setting: button.dataset.setting,
        value: button.dataset.value === "true",
      });
    });
  });

  app.querySelectorAll("[data-action='range-setting']").forEach((input) => {
    input.addEventListener("change", async () => {
      await postJson("/api/setting", {
        module: input.dataset.module,
        setting: input.dataset.setting,
        value: Number(input.value),
      });
    });
  });

  app.querySelectorAll("[data-action='text-setting']").forEach((input) => {
    const commit = async () => {
      await postJson("/api/setting", {
        module: input.dataset.module,
        setting: input.dataset.setting,
        value: input.value,
      });
    };
    input.addEventListener("change", commit);
    input.addEventListener("keydown", async (event) => {
      if (event.key === "Enter") {
        await commit();
      }
    });
  });

  app.querySelectorAll("[data-action='select-setting']").forEach((select) => {
    select.addEventListener("change", async () => {
      await postJson("/api/setting", {
        module: select.dataset.module,
        setting: select.dataset.setting,
        value: select.value,
      });
    });
  });

  app.querySelectorAll("[data-action='color-setting']").forEach((input) => {
    input.addEventListener("change", async () => {
      const module = getSelectedModule();
      const setting = module?.settings.find((entry) => entry.id === input.dataset.setting);
      if (!setting) {
        return;
      }

      const next = { ...setting.value, [input.dataset.channel]: Number(input.value) };
      await postJson("/api/setting", {
        module: input.dataset.module,
        setting: input.dataset.setting,
        value: next,
      });
    });
  });

  app.querySelectorAll("[data-action='capture-bind']").forEach((button) => {
    button.addEventListener("click", () => {
      state.captureBind = {
        moduleId: button.dataset.module,
        settingId: button.dataset.setting,
      };
      render();
    });
  });

  app.querySelectorAll("[data-action='reset-bind']").forEach((button) => {
    button.addEventListener("click", async () => {
      await postJson("/api/setting", {
        module: button.dataset.module,
        setting: button.dataset.setting,
        value: { key: -1 },
      });
    });
  });

  app.querySelectorAll("[data-action='close-drawer']").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedModuleId = null;
      render();
    });
  });

  const searchInput = app.querySelector("[data-role='search']");
  if (searchInput) {
    searchInput.addEventListener("input", () => {
      state.search = searchInput.value;
      render();
    });
  }
}

window.addEventListener("keydown", async (event) => {
  if (!state.captureBind) {
    return;
  }

  event.preventDefault();
  const key = event.code === "Escape" ? -1 : browserCodeToGlfw(event.code);
  if (key == null) {
    return;
  }

  const capture = state.captureBind;
  state.captureBind = null;
  await postJson("/api/setting", {
    module: capture.moduleId,
    setting: capture.settingId,
    value: { key },
  });
});

loadState().catch((error) => {
  app.innerHTML = `<div class="panel drawer-empty">${escapeHtml(error.message)}</div>`;
});
setInterval(() => {
  loadState().catch((error) => {
    console.error(error);
  });
}, 2000);
