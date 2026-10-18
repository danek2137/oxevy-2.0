import { useCallback, useEffect, useState, type ReactNode } from "react";

type IslandState = {
  fps?: number;
  ping?: number;
  memory?: { used?: number };
  player?: {
    pos?: { x: number; y: number; z: number };
    biome?: string;
  };
  world?: {
    time?: number;
    dimension?: string;
  };
};

type Metric = {
  key: string;
  label: string;
  value: string;
  icon: ReactNode;
  tone?: "good" | "warn";
  wide?: boolean;
};

const fallbackState: IslandState = {
  fps: 144,
  ping: 24,
  memory: { used: 449 * 1024 * 1024 },
  player: {
    pos: { x: 148.6, y: 64, z: -231.2 },
    biome: "Plains",
  },
  world: {
    time: Math.floor(Date.now() / 1000),
    dimension: "Overworld",
  },
};

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      aria-hidden="true"
      className="metric-icon"
      fill="none"
      viewBox="0 0 24 24"
    >
      {children}
    </svg>
  );
}

const icons = {
  fps: (
    <Icon>
      <path d="m13 2-8 12h7l-1 8 8-12h-7l1-8Z" />
    </Icon>
  ),
  ping: (
    <Icon>
      <path d="M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0" />
      <circle cx="12" cy="19.2" r="1" fill="currentColor" stroke="none" />
    </Icon>
  ),
  memory: (
    <Icon>
      <rect x="5" y="5" width="14" height="14" rx="3" />
      <rect x="9" y="9" width="6" height="6" rx="1" />
      <path d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3" />
    </Icon>
  ),
  coords: (
    <Icon>
      <circle cx="12" cy="12" r="9" />
      <path d="m15.5 8.5-2 5-5 2 2-5 5-2Z" />
    </Icon>
  ),
  time: (
    <Icon>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.5 2" />
    </Icon>
  ),
  biome: (
    <Icon>
      <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z" />
      <path d="M3.5 9h5L10 6l3 1 1-3M20.5 13H17l-2 2v4l-2 2M5 16l3-2 3 1v5" />
    </Icon>
  ),
  dimension: (
    <Icon>
      <path d="m12 3 8 4.5-8 4.5-8-4.5L12 3Z" />
      <path d="m4 12 8 4.5 8-4.5M4 16.5l8 4.5 8-4.5" />
    </Icon>
  ),
};

function getMetrics(data: IslandState): Metric[] {
  const fps = data.fps;
  const ping = data.ping;
  const memory = Math.round((data.memory?.used ?? 0) / 1024 / 1024);
  const position = data.player?.pos;
  const coords = position
    ? `${position.x.toFixed(1)}, ${position.y.toFixed(1)}, ${position.z.toFixed(1)}`
    : "—";
  const time = data.world?.time
    ? new Date(data.world.time * 1000).toISOString().slice(11, 19)
    : "—";

  return [
    {
      key: "fps",
      label: "FPS",
      value: fps?.toString() ?? "—",
      icon: icons.fps,
      tone: fps !== undefined && fps < 30 ? "warn" : "good",
    },
    {
      key: "ping",
      label: "Ping",
      value: ping !== undefined ? `${ping} ms` : "— ms",
      icon: icons.ping,
      tone: ping !== undefined && ping > 100 ? "warn" : "good",
    },
    {
      key: "memory",
      label: "Mem",
      value: `${memory} MB`,
      icon: icons.memory,
    },
    {
      key: "coords",
      label: "XYZ",
      value: coords,
      icon: icons.coords,
      wide: true,
    },
    {
      key: "time",
      label: "Time",
      value: time,
      icon: icons.time,
    },
    {
      key: "biome",
      label: "Biome",
      value: data.player?.biome ?? "—",
      icon: icons.biome,
    },
    {
      key: "dimension",
      label: "Dim",
      value: data.world?.dimension ?? "—",
      icon: icons.dimension,
    },
  ];
}

export default function App() {
  const [data, setData] = useState<IslandState>(fallbackState);
  const [connected, setConnected] = useState(false);

  const load = useCallback(async () => {
    try {
      const response = await fetch("/api/island/state");
      if (!response.ok) throw new Error("Island API unavailable");
      setData((await response.json()) as IslandState);
      setConnected(true);
    } catch {
      setConnected(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const interval = window.setInterval(() => void load(), 1000);
    return () => window.clearInterval(interval);
  }, [load]);

  const metrics = getMetrics(data);

  return (
    <main className="island-stage">
      <section className="island" aria-label="Live game status">
        <div className="island-glow" />
        <div className="connection" title={connected ? "Live data connected" : "Preview data"}>
          <span className={connected ? "is-live" : ""} />
        </div>

        <div className="island-content">
          {metrics.map((metric, index) => (
            <div className="metric-group" key={metric.key}>
              {index > 0 && <span className="divider" aria-hidden="true" />}
              <div className={`metric ${metric.wide ? "metric--wide" : ""}`}>
                <span className="icon-shell">{metric.icon}</span>
                <span className="metric-copy">
                  <span className="metric-label">{metric.label}</span>
                  <strong className={`metric-value ${metric.tone ? `is-${metric.tone}` : ""}`}>
                    {metric.value}
                  </strong>
                </span>
              </div>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
