/**
 * Handles stop selection, autocomplete, and journey search.
 * Fetches stop suggestions from the backend and displays
 * journey alternatives with comparison visuals.
 */

import { useEffect, useRef, useState } from "react";
import BottomNav from "../components/BottomNav";
import "../styles/routes.css";

const BACKEND = "http://localhost:8080";

function RoutesPage({ activePage, onNavigate }) {
    // Form state
    const [originText, setOriginText] = useState("");
    const [destinationText, setDestinationText] = useState("");

    // Stop search state
    const [originStop, setOriginStop] = useState(null);
    const [destinationStop, setDestinationStop] = useState(null);

    // Stop search options
    const [originOptions, setOriginOptions] = useState([]);
    const [destinationOptions, setDestinationOptions] = useState([]);

    // Loading states for stop search
    const [loadingOrigin, setLoadingOrigin] = useState(false);
    const [loadingDestination, setLoadingDestination] = useState(false);

    // Debounce refs for stop search
    const originDebounceRef = useRef(null);
    const destDebounceRef = useRef(null);

    // Journey search state
    const [routes, setRoutes] = useState([]);
    const [hasSearched, setHasSearched] = useState(false);

    // Loading and error states for journey search
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    // Fetch stop suggestions based on query, updating options and loading state
    async function fetchStopSuggestions(query, setOptions, setLoadingFlag) {
    const q = (query || "").trim();
    if (q.length < 2) {
        
        setOptions([]);
        return;
    }

    // Indicate loading state while fetching suggestions
    setLoadingFlag(true);
    try {
      const res = await fetch(
        `${BACKEND}/api/stops/search?query=${encodeURIComponent(q)}`
      );

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const data = await res.json();
      setOptions(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error("Stop search failed:", e);
      setOptions([]);
    } finally {
      setLoadingFlag(false);
    }
  }

  // Handlers for input changes, stop selection, and journey search
  function onOriginChange(value) {
    setOriginText(value);
    setOriginStop(null);

    if (originDebounceRef.current) clearTimeout(originDebounceRef.current);
    originDebounceRef.current = setTimeout(() => {
      fetchStopSuggestions(value, setOriginOptions, setLoadingOrigin);
    }, 250);
  }

  // Similar handler for destination input changes, with its own debounce
  function onDestinationChange(value) {
    setDestinationText(value);
    setDestinationStop(null);

    if (destDebounceRef.current) clearTimeout(destDebounceRef.current);
    destDebounceRef.current = setTimeout(() => {
      fetchStopSuggestions(value, setDestinationOptions, setLoadingDestination);
    }, 250);
  }

  // Handlers for when a stop is selected from the suggestions, updating state accordingly
  function pickOrigin(stop) {
    setOriginStop(stop);
    setOriginText(stop.name || stop.id);
    setOriginOptions([]);
  }

  // Similar handler for when a destination stop is selected
  function pickDestination(stop) {
    setDestinationStop(stop);
    setDestinationText(stop.name || stop.id);
    setDestinationOptions([]);
  }

  // Handler for when the "Go" button is clicked, initiating a journey search based on selected stops
  async function handleGo() {
    setHasSearched(true);
    setError("");

    if (!originStop?.id || !destinationStop?.id) {
      setRoutes([]);
      return;
    }

    // Indicate loading state while fetching journey options
    setLoading(true);
    try {
      const res = await fetch(`${BACKEND}/api/journeys/search`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          originStopId: originStop.id,
          destinationStopId: destinationStop.id,
        }),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

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

  // Determine if we should show a message about missing input after a search attempt
  const showMissingInput =
    hasSearched && (!originStop?.id || !destinationStop?.id);

  return (
    <div className="page routes-page">
      <div className="routes-header">
        <h2 className="routes-title">Route View</h2>
        <div className="routes-subtitle">Supporting decision-making</div>
      </div>

      <div className="routes-layout">
        <section className="routes-left">
          <div className="input-card">
            <StopAutocomplete
              pinClass="pin pin-green"
              value={originText}
              onChange={onOriginChange}
              onPick={pickOrigin}
              options={originOptions}
              loading={loadingOrigin}
              placeholder="Origin (type a stop name, e.g. Eyre Square)"
              selected={originStop}
            />

            <StopAutocomplete
              pinClass="pin pin-red"
              value={destinationText}
              onChange={onDestinationChange}
              onPick={pickDestination}
              options={destinationOptions}
              loading={loadingDestination}
              placeholder="Destination (type a stop name)"
              selected={destinationStop}
            />

            <button className="go-btn" type="button" onClick={handleGo}>
              Go
            </button>

            {hasSearched && showMissingInput && (
              <div className="empty-state" style={{ marginTop: 10 }}>
                Please select both stops from the dropdown.
              </div>
            )}
          </div>

          <div className="section-title">Route alternatives</div>

          {!hasSearched ? (
            <div className="empty-state">Enter a start and end, then hit Go.</div>
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

// Reusable component for stop autocomplete input, handling user input, displaying suggestions, and managing selection state
function StopAutocomplete({
  pinClass,
  value,
  onChange,
  onPick,
  options,
  loading,
  placeholder,
  selected,
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    function handleClickOutside(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <label className="input-row" style={{ position: "relative" }} ref={wrapRef}>
      <span className={pinClass}>●</span>

      <input
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        placeholder={placeholder}
        className="text-input"
        autoComplete="off"
      />

      {selected?.id && (
        <div
          style={{
            position: "absolute",
            right: 10,
            top: "50%",
            transform: "translateY(-50%)",
            fontSize: 12,
            opacity: 0.6,
          }}
          title={selected.id}
        >
          ID ✓
        </div>
      )}

      {open && (loading || options.length > 0) && (
        <div
          style={{
            position: "absolute",
            left: 0,
            right: 0,
            top: "calc(100% + 8px)",
            background: "white",
            border: "1px solid #e5e7eb",
            borderRadius: 12,
            boxShadow: "0 10px 30px rgba(0,0,0,0.08)",
            zIndex: 50,
            overflow: "hidden",
          }}
        >
          {loading ? (
            <div style={{ padding: 12, opacity: 0.7 }}>Searching…</div>
          ) : (
            options.map((s) => (
              <button
                key={s.id}
                type="button"
                onClick={() => {
                  onPick(s);
                  setOpen(false);
                }}
                style={{
                  display: "block",
                  width: "100%",
                  textAlign: "left",
                  padding: 12,
                  border: "none",
                  background: "white",
                  cursor: "pointer",
                }}
              >
                <div style={{ fontWeight: 600 }}>{s.name}</div>
                <div style={{ fontSize: 12, opacity: 0.65 }}>{s.id}</div>
              </button>
            ))
          )}
        </div>
      )}
    </label>
  );
}

// Component to display a single route option, showing its stops, duration, transfers, and whether it's recommended
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

// Helper function to render the list of stops in a route, showing their names and directional arrows between them
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
// Component to display comparative bars for multiple routes, showing their durations relative to each other and highlighting the recommended option
function ComparisonBars({ routes }) {
  const maxDuration = Math.max(...routes.map((r) => r.totalDurationSeconds || 0));

  return (
    <div className="bars">
      {routes.map((r) => {
        const duration = r.totalDurationSeconds || 0;
        const widthPct =
          maxDuration > 0 ? Math.round((duration / maxDuration) * 100) : 0;
        const isRec = r.recommended;
        const durationMinutes = Math.round(duration / 60);

        return (
          <div
            key={`${duration}-${r.transfers}-${r.recommended}`}
            className="bar-row"
          >
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
