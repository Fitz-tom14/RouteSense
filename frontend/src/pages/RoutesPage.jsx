/**
 * Handles stop selection, autocomplete, and journey search.
 * Fetches stop suggestions from the backend and displays
 * journey alternatives with comparison visuals.
 */

import { useEffect, useRef, useState } from "react";
import { MapContainer, TileLayer, Marker, useMapEvents } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import BottomNav from "../components/BottomNav";
import "../styles/routes.css";

// Fix Leaflet default marker icons in Vite.
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",// Using unpkg CDN for marker icons to avoid build issues with Leaflet's default icon paths
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",// Using unpkg CDN for marker icons to avoid build issues with Leaflet's default icon paths
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",// Using unpkg CDN for marker icons to avoid build issues with Leaflet's default icon paths
});

const BACKEND = "http://localhost:8080";

// const BACKEND = "https://api.routesense.com";
function RoutesPage({ activePage, onNavigate }) {
  const [originText, setOriginText] = useState("");
  const [destinationText, setDestinationText] = useState("");

  const [originStop, setOriginStop] = useState(null);
  const [originPin, setOriginPin] = useState(null); // { lat, lng } from map click
  const [destinationStop, setDestinationStop] = useState(null);

  const [originOptions, setOriginOptions] = useState([]);
  const [destinationOptions, setDestinationOptions] = useState([]);

  const [loadingOrigin, setLoadingOrigin] = useState(false);
  const [loadingDestination, setLoadingDestination] = useState(false);

  const originDebounceRef = useRef(null);
  const destDebounceRef = useRef(null);

  const [publicRoutes, setPublicRoutes] = useState([]);
  const [carBaseline, setCarBaseline] = useState(null);
  const [carBaselineCo2Grams, setCarBaselineCo2Grams] = useState(0);
  const [hasSearched, setHasSearched] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // Fetches stop suggestions from the backend based on the query, and updates the options and loading state accordingly.
  async function fetchStopSuggestions(query, setOptions, setLoadingFlag) {
    const trimmed = (query || "").trim();
    if (trimmed.length < 2) {
      setOptions([]);
      return;
    }

    setLoadingFlag(true);
    try {
      const res = await fetch(
        `${BACKEND}/api/stops/search?query=${encodeURIComponent(trimmed)}`
      );
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }

      // Expecting an array of stop objects with { id, name } from the backend
      const data = await res.json();
      setOptions(Array.isArray(data) ? data : []);
    } catch (requestError) {
      console.error("Stop search failed:", requestError);
      setOptions([]);
    } finally {
      setLoadingFlag(false);
    }
  }

  // Handles changes to the origin input field, updating state and fetching new suggestions with debouncing to avoid excessive requests.
  function onOriginChange(value) {
    setOriginText(value);
    setOriginStop(null);
    setOriginPin(null); // typing clears any map pin

    // Clear any existing debounce timer before starting a new one
    if (originDebounceRef.current) {
      clearTimeout(originDebounceRef.current);
    }

    // Start a new debounce timer to fetch stop suggestions after a short delay
    // This allows the user to type without triggering a request on every keystroke, improving performance and reducing load on the backend.
    originDebounceRef.current = setTimeout(() => {
      fetchStopSuggestions(value, setOriginOptions, setLoadingOrigin);
    }, 250);
  }

  // Handles picking a location on the map as the origin, setting the originPin state and clearing any selected origin stop or text input, 
  // since the user is choosing to specify their origin via the map instead of typing a stop name.
  function handleMapPick(coords) {
    setOriginPin(coords);
    setOriginStop(null);
    setOriginText("");
    setOriginOptions([]);
  }

  // Clears the origin pin selection, allowing the user to go back to typing an origin stop if they change their mind after picking a point on the map.
  function clearOriginPin() {
    setOriginPin(null);
  }

  // Handles changes to the destination input field, updating state and fetching new suggestions with debouncing to avoid excessive requests.
  function onDestinationChange(value) {
    setDestinationText(value);
    setDestinationStop(null);

    if (destDebounceRef.current) {
      clearTimeout(destDebounceRef.current);
    }

    destDebounceRef.current = setTimeout(() => {
      fetchStopSuggestions(value, setDestinationOptions, setLoadingDestination);
    }, 250);
  }

  // Handles picking an origin stop from the autocomplete suggestions, updating the selected origin stop and text, and clearing the options dropdown.
  function pickOrigin(stop) {
    setOriginStop(stop);
    setOriginText(stop.name || stop.id);
    setOriginOptions([]);
  }

  // Handles picking a destination stop from the autocomplete suggestions, updating the selected destination stop and text, and clearing the options dropdown.
  function pickDestination(stop) {
    setDestinationStop(stop);
    setDestinationText(stop.name || stop.id);
    setDestinationOptions([]);
  }

  // Handles the "Go" button click, validating inputs and making a request to the backend to search for journey options based on the selected origin and destination.
  async function handleGo() {
    setHasSearched(true);
    setError("");

    // Validate that we have either an origin stop or an origin pin, and that we have a destination stop. If not, show an error message and clear any existing routes or baseline data.
    const hasOrigin = originStop?.id || originPin;
    if (!hasOrigin || !destinationStop?.id) {
      setPublicRoutes([]);
      setCarBaseline(null);
      setCarBaselineCo2Grams(0);
      setError("Please select a destination stop and either type an origin stop or pick a point on the map.");
      return;
    }

    // Make a POST request to the backend with the origin and destination information to search for journey options. The request body includes either the origin stop ID or the origin coordinates from the map, along with the destination stop ID.
    setLoading(true);
    try {
      const body = { destinationStopId: destinationStop.id };
      if (originStop?.id) {
        body.originStopId = originStop.id;
      } else {
        body.originLat = originPin.lat;
        body.originLon = originPin.lng;
      }

      // The backend is expected to return a JSON response containing an array of journey options, each with details like duration, CO₂ emissions, and transport mode, as well as a car baseline option for comparison. We parse the response and update the state with the new routes and baseline data, or show an error message if the request fails.
      const res = await fetch(`${BACKEND}/api/journeys/search`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }

      // The backend response can be in two formats: either a simple array of route options, or an object containing an "options" array along with additional metadata like the car baseline CO₂.
      //  We handle both cases to maintain flexibility in the backend response format.
      const data = await res.json();
      const options = Array.isArray(data) ? data : data?.options;// Normalize the options to an array, defaulting to an empty array if the data is not in the expected format, to avoid errors when rendering the routes list.
      const normalizedOptions = Array.isArray(options) ? options : [];

      // We separate out the car baseline option from the public transport options based on the "type" field, 
      // which allows us to display them differently in the UI and use the car baseline CO₂ for comparison.
      const carOption =
        normalizedOptions.find((option) => option?.type === "CAR_BASELINE") || null;
      const ptOptions = normalizedOptions.filter(
        (option) => option?.type !== "CAR_BASELINE"
      );

      const responseCarBaselineCo2 =
        typeof data?.carBaselineCo2Grams === "number"
          ? data.carBaselineCo2Grams
          : carOption?.co2Grams || 0;

      setPublicRoutes(ptOptions);
      setCarBaseline(carOption);
      setCarBaselineCo2Grams(responseCarBaselineCo2);
      setError("");
    } catch (requestError) {
      console.error("Journey search failed:", requestError);
      setPublicRoutes([]);
      setCarBaseline(null);
      setCarBaselineCo2Grams(0);
      setError("We couldn’t load journey options right now. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  // Determines whether to show a message about missing input based on whether the user has attempted a search and whether the required inputs are present. 
  // This helps guide the user to provide the necessary information to perform a search.
  const showMissingInput =
    hasSearched && (!(originStop?.id || originPin) || !destinationStop?.id);

  return (
    // The main layout of the RoutesPage, which includes a header, a left section for input and route alternatives, and a right section for the map and comparison panel.
    //  The page also includes a bottom navigation bar.
    <div className="page routes-page">
      <div className="routes-header">
        <h2 className="routes-title">Route View</h2>
        <div className="routes-subtitle">Supporting decision-making</div>
      </div>

      // The main content area is divided into two sections: the left side for user input and displaying route alternatives, and the right side for the map and comparison panel. This layout allows users to easily interact with the inputs and see the resulting routes and comparisons in a clear and organized way.
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
                {!(originStop?.id || originPin)
                  ? "Please type an origin stop or pick a point on the map."
                  : "Please select a destination stop from the dropdown."}
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
          ) : !publicRoutes.length && !carBaseline ? (
            <div className="empty-state">No journey options found.</div>
          ) : (
            <div className="routes-list">
              {publicRoutes.map((route, index) => (
                <RouteCard
                  key={`${route.durationSeconds}-${route.transfers}-${index}`}
                  route={route}
                  carBaselineCo2Grams={carBaselineCo2Grams}
                />
              ))}

              {carBaseline && <CarBaselineCard route={carBaseline} />}
            </div>
          )}
        </section>

        <section className="routes-right">
          <div className="panel origin-map-panel">
            <div className="panel-label">Pick your start point on the map</div>
            <div className="origin-map-wrap">
              <OriginPickerMap pin={originPin} onPick={handleMapPick} />
            </div>
            {originPin ? (
              <div className="pin-info">
                <span>📍 {originPin.lat.toFixed(4)}, {originPin.lng.toFixed(4)}</span>
                <button className="clear-pin-btn" type="button" onClick={clearOriginPin}>✕ Clear</button>
              </div>
            ) : (
              <div className="panel-empty">Click anywhere on the map to set your start point, or type a stop name on the left.</div>
            )}
          </div>

          {hasSearched && (publicRoutes.length > 0 || carBaseline) && (
            <ComparisonPanel
              publicRoutes={publicRoutes}
              carBaseline={carBaseline}
            />
          )}
        </section>
      </div>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

// The StopAutocomplete component renders an input field with autocomplete functionality for selecting stops. 
// It also supports selecting a location on the map as an origin, and displays a dropdown of suggestions based on user input. 
// The component manages its own open/close state for the dropdown and handles clicks outside to close it.
export default RoutesPage;

// The StopAutocomplete component renders an input field with autocomplete functionality for selecting stops.
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
  // Manages the open/close state of the autocomplete dropdown and sets up a click listener to close the dropdown when clicking outside of it.
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  // Sets up a click listener on the document to detect clicks outside of the component, which will close the autocomplete dropdown when it is open.
  //  This improves the user experience by allowing them to easily dismiss the suggestions list when they click elsewhere on the page.
  useEffect(() => {
    function handleClickOutside(event) {
      if (wrapRef.current && !wrapRef.current.contains(event.target)) {
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
        onChange={(event) => {
          onChange(event.target.value);
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
            zIndex: 9999,
            overflow: "hidden",
          }}
        >
          {loading ? (
            <div style={{ padding: 12, opacity: 0.7 }}>Searching…</div>
          ) : (
            options.map((stop) => (
              <button
                key={stop.id}
                type="button"
                onClick={() => {
                  onPick(stop);
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
                <div style={{ fontWeight: 600 }}>{stop.name}</div>
                <div style={{ fontSize: 12, opacity: 0.65 }}>{stop.id}</div>
              </button>
            ))
          )}
        </div>
      )}
    </label>
  );
}

// The RouteCard component renders a card for a single route option, displaying key information such as duration, transfers, estimated CO₂ emissions, and the stops involved in the route.
function RouteCard({ route, carBaselineCo2Grams }) {
  const durationSeconds = route.durationSeconds ?? route.totalDurationSeconds ?? 0;
  const durationMinutes = secondsToMins(durationSeconds);
  const co2Grams = route.co2Grams || 0;
  const co2SavedGrams = Math.max(0, carBaselineCo2Grams - co2Grams);
  const modeLabel = route.modeSummary || formatRouteType(route.type);

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
            <div className="metric-label">Total time</div>
          </div>

          <div className="metric">
            <div className="metric-value">{route.transfers || 0}</div>
            <div className="metric-label">Transfers</div>
          </div>

          <div className="metric">
            <div className="metric-value">{formatCo2(co2Grams)}</div>
            <div className="metric-label">Estimated CO₂</div>
          </div>
        </div>

        {route.recommended && route.recommendationReason && (
          <div className="route-notes">{route.recommendationReason}</div>
        )}

        <div className="route-notes">Mode: {modeLabel}</div>
        <div className="route-notes">CO₂ saved vs car: {formatCo2(co2SavedGrams)}</div>
        <div className="route-notes">{route.stops?.length || 0} stops</div>
      </div>
    </div>
  );
}

// The CarBaselineCard component renders a card for the baseline car route, displaying key information such as estimated drive time and CO₂ emissions.
//  This card is used for comparison purposes and is never auto-recommended.
function CarBaselineCard({ route }) {
  const durationSeconds = route.durationSeconds ?? route.totalDurationSeconds ?? 0;
  return (
    <div className="route-card" style={{ borderStyle: "dashed" }}>
      <div className="route-top">
        <div className="route-legs">Driving (baseline estimate)</div>
      </div>

      <div className="route-bottom">
        <div className="route-metrics">
          <div className="metric">
            <div className="metric-value">{secondsToMins(durationSeconds)} min</div>
            <div className="metric-label">Estimated drive time</div>
          </div>

          <div className="metric">
            <div className="metric-value">0</div>
            <div className="metric-label">Transfers</div>
          </div>

          <div className="metric">
            <div className="metric-value">{formatCo2(route.co2Grams || 0)}</div>
            <div className="metric-label">Estimated CO₂</div>
          </div>
        </div>

        <div className="route-notes">Comparison baseline only (never auto-recommended).</div>
      </div>
    </div>
  );
}

// Renders the sequence of stops for a route option, showing the stop names with icons and arrows between them. 
// If no stop information is available, it shows a generic "Route option" label.
function renderStops(stops = []) {
  const names = stops.map((stop) => stop.name).filter(Boolean);

  if (names.length === 0) {
    return <div className="legs-row">Route option</div>;
  }

  return (
    <div className="legs-row">
      {names.map((name, index) => (
        <span key={`${name}-${index}`} className="leg-item">
          <span className="leg-icon">📍</span>
          <span>{name}</span>
          {index !== names.length - 1 && <span className="leg-arrow">›</span>}
        </span>
      ))}
    </div>
  );
}

/**
 * Shows a compact bar chart comparing all route options by time and CO₂.
 * Appears below the map after a search is completed.
 */
function ComparisonPanel({ publicRoutes, carBaseline }) {
  const allOptions = [
    ...publicRoutes.map((r, i) => ({
      label: r.modeSummary || `Option ${i + 1}`,
      durationSeconds: r.durationSeconds ?? r.totalDurationSeconds ?? 0,
      co2Grams: r.co2Grams || 0,
      recommended: r.recommended,
      isCar: false,
    })),
    ...(carBaseline ? [{
      label: "Car (baseline)",
      durationSeconds: carBaseline.durationSeconds ?? carBaseline.totalDurationSeconds ?? 0,
      co2Grams: carBaseline.co2Grams || 0,
      recommended: false,
      isCar: true,
    }] : []),
  ];

  const maxDuration = Math.max(...allOptions.map((o) => o.durationSeconds), 1);

  // The comparison panel renders a horizontal bar chart comparing the duration and CO₂ emissions of all route options, including the car baseline if available.
  //  Each option is represented by a bar whose length corresponds to its duration relative to the longest option, and the color indicates whether it is recommended or not. 
  // The panel also includes a legend to explain the colors used for recommended and other options.
  return (
    <div className="panel" style={{ marginTop: 12 }}>
      <div className="section-title" style={{ margin: "0 0 10px" }}>Quick comparison</div>
      <div className="bars">
        {allOptions.map((opt, i) => (
          <div key={i} className="bar-row">
            <div className="bar-left">
              <span>{opt.isCar ? "🚗" : opt.recommended ? "★" : "🚌"}</span>
              {opt.recommended && <span className="mini-star"> </span>}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="bar-track">
                <div
                  className={`bar-fill ${opt.recommended ? "bar-rec" : ""}`}
                  style={{
                    width: `${(opt.durationSeconds / maxDuration) * 100}%`,
                    ...(opt.isCar ? { background: "rgba(180, 80, 80, 0.45)" } : {}),
                  }}
                />
              </div>
              <div style={{ fontSize: 11, color: "#8a95a6", marginTop: 2, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{opt.label}</div>
            </div>
            <div className="bar-right">
              <div className="bar-time">{secondsToMins(opt.durationSeconds)} min</div>
              <div className="bar-co2">{formatCo2(opt.co2Grams)} CO₂</div>
            </div>
          </div>
        ))}
      </div>
      <div className="legend">
        <span className="legend-dot rec" />Recommended
        <span className="legend-dot normal" style={{ marginLeft: 8 }} />Other
      </div>
    </div>
  );
}

/**
 * Registers a click listener on the Leaflet map.
 * Must be rendered inside a MapContainer.
 */
function MapClickHandler({ onPick }) {
  useMapEvents({
    click(e) {
      onPick({ lat: e.latlng.lat, lng: e.latlng.lng });
    },
  });
  return null;
}

/**
 * Renders a small Leaflet map centred on Ireland.
 * Clicking anywhere drops a pin and calls onPick({ lat, lng }).
 */
function OriginPickerMap({ pin, onPick }) {
  return (
    <MapContainer
      center={[53.4, -8.0]}
      zoom={6}
      style={{ height: "100%", width: "100%", borderRadius: 10 }}
    >
      <TileLayer
        attribution="&copy; OpenStreetMap contributors"
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <MapClickHandler onPick={onPick} />
      {pin && <Marker position={[pin.lat, pin.lng]} />}
    </MapContainer>
  );
}

// Utility function to convert seconds to rounded minutes, handling null or undefined values by treating them as zero.
function secondsToMins(seconds) {
  return Math.round((seconds || 0) / 60);
}

// Utility function to format CO₂ emissions in grams, converting to kilograms if the value is 1000 grams or more, and ensuring that negative values are treated as zero for display purposes.
function formatCo2(grams) {
  const safeGrams = Math.max(0, grams || 0);
  if (safeGrams >= 1000) {
    return `${(safeGrams / 1000).toFixed(1)} kg`;
  }
  return `${Math.round(safeGrams)} g`;
}

// Utility function to format the route type into a more user-friendly label, handling various known types and defaulting to "Public transport" for any unrecognized types.
// This function ensures that the transport mode is displayed in a clear and consistent way for the user, improving the readability of the route information.
function formatRouteType(type) {
  const value = (type || "PUBLIC_TRANSPORT").toUpperCase();
  if (value === "WALKING") {
    return "Walking";
  }
  if (value === "CYCLING") {
    return "Bike";
  }
  if (value === "BUS") {
    return "Bus";
  }
  if (value === "TRAIN") {
    return "Train";
  }
  if (value === "CAR_BASELINE") {
    return "Car";
  }
  return "Public transport";
}
