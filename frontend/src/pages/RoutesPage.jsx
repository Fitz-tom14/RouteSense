import { useMemo, useState } from "react";
import BottomNav from "../components/BottomNav";
import "../styles/routes.css";
import { buildRoutesDijkstra } from "../services/routeEngine";

/**
 * Routes page (Dijkstra version)
 * - User enters Start + End
 * - Click "Go"
 * - We generate route alternatives using Dijkstra on a demo graph
 * - We recommend the "Balanced" option
 */
function RoutesPage({ activePage, onNavigate }) {
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [hasSearched, setHasSearched] = useState(false);

  const { routes, recommendedId } = useMemo(() => {
    if (!hasSearched) return { routes: [], recommendedId: null };
    return buildRoutesDijkstra(start, end);
  }, [hasSearched, start, end]);

  function handleGo() {
    setHasSearched(true);
  }

  const showMissingInput = hasSearched && (!start.trim() || !end.trim());

  return (
    <div className="page routes-page">
      <div className="routes-header">
        <h2 className="routes-title">Route View</h2>
        <div className="routes-subtitle">Supporting decision-making</div>
      </div>

      <div className="routes-layout">
        {/* LEFT COLUMN */}
        <section className="routes-left">
          <div className="input-card">
            <label className="input-row">
              <span className="pin pin-green">●</span>
              <input
                value={start}
                onChange={(e) => setStart(e.target.value)}
                placeholder="Start (e.g. Eyre Square)"
                className="text-input"
              />
            </label>

            <label className="input-row">
              <span className="pin pin-red">●</span>
              <input
                value={end}
                onChange={(e) => setEnd(e.target.value)}
                placeholder="End (e.g. Salthill)"
                className="text-input"
              />
            </label>

            <button className="go-btn" type="button" onClick={handleGo}>
              Go
            </button>
          </div>

          <div className="section-title">Route alternatives</div>

          {!hasSearched ? (
            <div className="empty-state">Enter a start and end, then hit Go.</div>
          ) : showMissingInput ? (
            <div className="empty-state">Please enter both Start and End.</div>
          ) : !routes.length ? (
            <div className="empty-state">
              No demo route found. Try: Eyre Square, NUIG, Ceannt Station, Oranmore, Salthill.
            </div>
          ) : (
            <div className="routes-list">
              {routes.map((r) => (
                <RouteCard
                  key={r.id}
                  route={r}
                  isRecommended={r.id === recommendedId}
                />
              ))}
            </div>
          )}
        </section>

        {/* RIGHT COLUMN */}
        <section className="routes-right">
          <div className="panel">
            <div className="panel-tabs">
              <div className="tab active">Time vs CO₂</div>
              <div className="tab muted">Route map</div>
            </div>

            {!routes.length ? (
              <div className="panel-empty">Run a search to compare options.</div>
            ) : (
              <ComparisonBars routes={routes} recommendedId={recommendedId} />
            )}
          </div>

          <button
            className="view-map-btn"
            type="button"
            onClick={() => onNavigate("Map")}
          >
            View map
          </button>
        </section>
      </div>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default RoutesPage;

/* ------------------------------ UI BITS ------------------------------ */

function RouteCard({ route, isRecommended }) {
  return (
    <div className={`route-card ${isRecommended ? "recommended" : ""}`}>
      <div className="route-top">
        <div className="route-legs">{renderLegIcons(route.legs)}</div>

        {isRecommended && <div className="badge">★ Recommended</div>}
      </div>

      <div className="route-bottom">
        <div className="route-metrics">
          <div className="metric">
            <div className="metric-value">{route.timeMin} min</div>
            <div className="metric-label">Time</div>
          </div>

          <div className="metric">
            <div className="metric-value">{route.co2Kg} kg</div>
            <div className="metric-label">CO₂</div>
          </div>

          <div className="metric">
            <div className="metric-value">{route.transfers}</div>
            <div className="metric-label">Transfers</div>
          </div>
        </div>

        <div className="route-notes">{route.notes}</div>
      </div>
    </div>
  );
}

function renderLegIcons(legs) {
  const icon = (leg) => {
    if (leg === "BUS") return "🚌";
    if (leg === "TRAIN") return "🚆";
    if (leg === "WALK") return "🚶";
    if (leg === "CYCLE") return "🚲";
    return "•";
  };

  return (
    <div className="legs-row">
      {legs.map((leg, i) => (
        <span key={leg + i} className="leg-item">
          <span className="leg-icon">{icon(leg)}</span>
          {i !== legs.length - 1 && <span className="leg-arrow">›</span>}
        </span>
      ))}
    </div>
  );
}

function ComparisonBars({ routes, recommendedId }) {
  const maxTime = Math.max(...routes.map((r) => r.timeMin));

  return (
    <div className="bars">
      {routes.map((r) => {
        const widthPct = Math.round((r.timeMin / maxTime) * 100);
        const isRec = r.id === recommendedId;

        return (
          <div key={r.id} className="bar-row">
            <div className="bar-left">
              <span className="bar-icon">{mainModeIcon(r.legs)}</span>
              {isRec && <span className="mini-star">★</span>}
            </div>

            <div className="bar-track">
              <div
                className={`bar-fill ${isRec ? "bar-rec" : ""}`}
                style={{ width: `${widthPct}%` }}
                title={`${r.timeMin} min, ${r.co2Kg} kg CO₂`}
              />
            </div>

            <div className="bar-right">
              <div className="bar-time">{r.timeMin} min</div>
              <div className="bar-co2">{r.co2Kg} kg CO₂</div>
            </div>
          </div>
        );
      })}

      <div className="legend">
        <span className="legend-dot rec" /> Recommended
        <span className="legend-dot normal" /> Other
      </div>
    </div>
  );
}

function mainModeIcon(legs) {
  if (legs.includes("CYCLE")) return "🚲";
  if (legs.includes("TRAIN")) return "🚆";
  if (legs.includes("BUS")) return "🚌";
  return "🧭";
}
