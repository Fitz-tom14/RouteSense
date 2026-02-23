import { useState } from "react";
import BottomNav from "../components/BottomNav";
import "../styles/routes.css";

function RoutesPage({ activePage, onNavigate }) {
  const [originStopId, setOriginStopId] = useState("");
  const [destinationStopId, setDestinationStopId] = useState("");
  const [routes, setRoutes] = useState([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleGo() {
    setHasSearched(true);
    setError("");

    if (!originStopId.trim() || !destinationStopId.trim()) {
      setRoutes([]);
      return;
    }

    setLoading(true);
    try {
      const res = await fetch("http://localhost:8080/api/journeys/search", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          originStopId: originStopId.trim(),
          destinationStopId: destinationStopId.trim(),
        }),
      });

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      setRoutes(Array.isArray(data) ? data : []);
    } catch (e) {
      setRoutes([]);
      setError("Unable to load journey options.");
      console.error("Journey search failed:", e);
    } finally {
      setLoading(false);
    }
  }

  const showMissingInput = hasSearched && (!originStopId.trim() || !destinationStopId.trim());

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
                value={originStopId}
                onChange={(e) => setOriginStopId(e.target.value)}
                placeholder="Origin stop ID"
                className="text-input"
              />
            </label>

            <label className="input-row">
              <span className="pin pin-red">●</span>
              <input
                value={destinationStopId}
                onChange={(e) => setDestinationStopId(e.target.value)}
                placeholder="Destination stop ID"
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
          ) : loading ? (
            <div className="empty-state">Loading journey options…</div>
          ) : error ? (
            <div className="empty-state">{error}</div>
          ) : !routes.length ? (
            <div className="empty-state">No journey options found.</div>
          ) : (
            <div className="routes-list">
              {routes.map((r, index) => (
                <RouteCard
                  key={`${r.totalDurationSeconds}-${r.transfers}-${index}`}
                  route={r}
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
              <ComparisonBars routes={routes} />
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

function RouteCard({ route }) {
  const durationMinutes = Math.round((route.totalDurationSeconds || 0) / 60);

  return (
    <div className={`route-card ${route.recommended ? "recommended" : ""}`}>
      <div className="route-top">
        <div className="route-legs">{renderStops(route.stops)}</div>

        {route.recommended && <div className="badge">★ Recommended</div>}
      </div>

      <div className="route-bottom">
        <div className="route-metrics">
          <div className="metric">
            <div className="metric-value">{durationMinutes} min</div>
            <div className="metric-label">Time</div>
          </div>

          <div className="metric">
            <div className="metric-value">{route.transfers}</div>
            <div className="metric-label">Transfers</div>
          </div>
        </div>

        <div className="route-notes">{route.stops?.length || 0} stops</div>
      </div>
    </div>
  );
}

function renderStops(stops = []) {
  const names = stops.map((stop) => stop.name).filter(Boolean);

  if (names.length === 0) {
    return <div className="legs-row">Route option</div>;
  }

  return (
    <div className="legs-row">
      {names.map((name, i) => (
        <span key={name + i} className="leg-item">
          <span className="leg-icon">📍</span>
          <span>{name}</span>
          {i !== names.length - 1 && <span className="leg-arrow">›</span>}
        </span>
      ))}
    </div>
  );
}

function ComparisonBars({ routes }) {
  const maxDuration = Math.max(...routes.map((r) => r.totalDurationSeconds || 0));

  return (
    <div className="bars">
      {routes.map((r) => {
        const duration = r.totalDurationSeconds || 0;
        const widthPct = maxDuration > 0 ? Math.round((duration / maxDuration) * 100) : 0;
        const isRec = r.recommended;
        const durationMinutes = Math.round(duration / 60);

        return (
          <div key={`${duration}-${r.transfers}-${r.recommended}`} className="bar-row">
            <div className="bar-left">
              <span className="bar-icon">🧭</span>
              {isRec && <span className="mini-star">★</span>}
            </div>

            <div className="bar-track">
              <div
                className={`bar-fill ${isRec ? "bar-rec" : ""}`}
                style={{ width: `${widthPct}%` }}
                title={`${durationMinutes} min, ${r.transfers} transfers`}
              />
            </div>

            <div className="bar-right">
              <div className="bar-time">{durationMinutes} min</div>
              <div className="bar-co2">{r.transfers} transfers</div>
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
