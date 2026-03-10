// RoutesPage component allows users to search for journey options between an origin and destination, either by typing stop names or picking points on the map. It fetches route options from the backend and displays them in a list, along with a map visualization of the routes. The user can also specify an "arrive by" time to filter routes accordingly. The page includes error handling and loading states, and compares public transport options against a car baseline in terms of duration and CO₂ emissions.
// The component manages a variety of state variables to track user input, search results, loading states, and errors. It includes functions to handle user interactions such as changing the origin/destination inputs, picking locations on the map, and initiating the search for routes. The search results are displayed in a list format, and the map shows the routes visually with different colors for public transport and car options.

import { useEffect, useRef, useState } from "react";
import { MapContainer, TileLayer, Marker, Polyline, useMap, useMapEvents } from "react-leaflet";
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
function RoutesPage({ activePage, onNavigate, onSelectJourney }) {
  const [originText, setOriginText] = useState("");
  const [destinationText, setDestinationText] = useState("");

  const [originStop, setOriginStop] = useState(null);
  const [originPin, setOriginPin] = useState(null);      // { lat, lng } from map click
  const [destinationStop, setDestinationStop] = useState(null);
  const [destinationPin, setDestinationPin] = useState(null); // { lat, lng } from map click
  const [mapMode, setMapMode] = useState("origin"); // "origin" | "destination"

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

  const [arriveByTime, setArriveByTime] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [routeGeometries, setRouteGeometries] = useState([]);
  const [carGeometry, setCarGeometry] = useState(null);
  const [walkGeometries, setWalkGeometries] = useState([]);

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

  function handleDestMapPick(coords) {
    setDestinationPin(coords);
    setDestinationStop(null);
    setDestinationText("");
    setDestinationOptions([]);
  }

  function clearDestinationPin() {
    setDestinationPin(null);
  }

  // Handles changes to the destination input field, updating state and fetching new suggestions with debouncing to avoid excessive requests.
  function onDestinationChange(value) {
    setDestinationText(value);
    setDestinationStop(null);
    setDestinationPin(null); // typing clears any map pin

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

    const hasOrigin      = originStop?.id || originPin;
    const hasDestination = destinationStop?.id || destinationPin;
    if (!hasOrigin || !hasDestination) {
      setPublicRoutes([]);
      setCarBaseline(null);
      setCarBaselineCo2Grams(0);
      setError("Please set both an origin and a destination — type a stop name or pick a point on the map.");
      return;
    }

    setLoading(true);
    try {
      const body = {};
      if (originStop?.id) {
        body.originStopId = originStop.id;
      } else {
        body.originLat = originPin.lat;
        body.originLon = originPin.lng;
      }
      if (destinationStop?.id) {
        body.destinationStopId = destinationStop.id;
      } else {
        body.destinationLat = destinationPin.lat;
        body.destinationLon = destinationPin.lng;
      }

      // If the user set an "arrive by" time, send it directly so the backend can
      // search from 3 hours before and filter out routes that arrive too late.
      if (arriveByTime) {
        const [hours, minutes] = arriveByTime.split(":").map(Number);
        body.arriveBySeconds = hours * 3600 + minutes * 60;
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
      setRouteGeometries([]);
      setCarGeometry(null);
      setWalkGeometries([]);
      setError("");

      // Car baseline geometry comes directly from ORS via the backend (reliable, no external call needed).
      // Fall back to OSRM only if the backend didn't return it (e.g. no ORS key configured).
      if (data?.carRouteGeometry && Array.isArray(data.carRouteGeometry)) {
        setCarGeometry(data.carRouteGeometry);
      } else if (carOption) {
        fetchOsrmGeometry(carOption.stops).then(setCarGeometry);
      }

      // Fetch road-following geometry from OSRM for PT routes (non-blocking)
      fetchAllOsrmGeometries(ptOptions).then(setRouteGeometries);

      // Fetch foot-profile OSRM geometry for walk legs (origin pin → first stop, last stop → dest pin)
      const pinLat     = originPin?.lat ?? null;
      const pinLng     = originPin?.lng ?? null;
      const destPinLat = destinationPin?.lat ?? null;
      const destPinLng = destinationPin?.lng ?? null;
      fetchAllWalkGeometries(ptOptions, pinLat, pinLng, destPinLat, destPinLng).then(setWalkGeometries);
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
    hasSearched && (!(originStop?.id || originPin) || !(destinationStop?.id || destinationPin));

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

            <div className="input-row">
              <span className="pin">🕐</span>
              <div className="time-input-wrap">
                <span className="time-label">Arrive at (optional)</span>
                <input
                  type="time"
                  value={arriveByTime}
                  onChange={(e) => setArriveByTime(e.target.value)}
                  className="text-input"
                />
                <span className="time-hint">Leave blank to search from now</span>
              </div>
            </div>

            <button className="go-btn" type="button" onClick={handleGo}>
              Go
            </button>

            {hasSearched && showMissingInput && (
              <div className="empty-state" style={{ marginTop: 10 }}>
                {!(originStop?.id || originPin)
                  ? "Please type an origin stop or pick a point on the map."
                  : "Please type a destination stop or pick a point on the map."}
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
                  onSelect={onSelectJourney ? (r) => onSelectJourney(r, carBaselineCo2Grams) : undefined}
                />
              ))}

              {carBaseline && <CarBaselineCard route={carBaseline} />}
            </div>
          )}
        </section>

        <section className="routes-right">
          <div className="panel origin-map-panel">
            <div className="panel-label">Pick points on the map</div>
            <div className="map-mode-toggle">
              <button
                type="button"
                className={`map-mode-btn${mapMode === "origin" ? " active" : ""}`}
                onClick={() => setMapMode("origin")}
              >
                🟢 Set origin
              </button>
              <button
                type="button"
                className={`map-mode-btn${mapMode === "destination" ? " active" : ""}`}
                onClick={() => setMapMode("destination")}
              >
                🔴 Set destination
              </button>
            </div>
            <div className="origin-map-wrap">
              <OriginPickerMap
                originPin={originPin}
                destinationPin={destinationPin}
                mapMode={mapMode}
                onPickOrigin={handleMapPick}
                onPickDestination={handleDestMapPick}
                routeLines={buildRouteLines(publicRoutes, carBaseline, routeGeometries, carGeometry, originPin, walkGeometries, destinationPin)}
              />
            </div>
            <div className="pin-info-row">
              {originPin ? (
                <div className="pin-info">
                  <span>🟢 {originPin.lat.toFixed(4)}, {originPin.lng.toFixed(4)}</span>
                  <button className="clear-pin-btn" type="button" onClick={clearOriginPin}>✕</button>
                </div>
              ) : (
                <div className="panel-empty" style={{ fontSize: 12 }}>No origin pin set</div>
              )}
              {destinationPin ? (
                <div className="pin-info">
                  <span>🔴 {destinationPin.lat.toFixed(4)}, {destinationPin.lng.toFixed(4)}</span>
                  <button className="clear-pin-btn" type="button" onClick={clearDestinationPin}>✕</button>
                </div>
              ) : (
                <div className="panel-empty" style={{ fontSize: 12 }}>No destination pin set</div>
              )}
            </div>
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

/**
 * Fetches a road-following polyline from OSRM for an array of stop objects.
 * Returns an array of [lat, lng] pairs, or null if the request fails.
 */
async function fetchOsrmGeometry(stops) {
  const valid = (stops || []).filter((s) => s.latitude != null && s.longitude != null);
  if (valid.length < 2) return null;

  // Subsample to at most 25 waypoints to stay within OSRM limits
  const sampled = valid.length <= 25 ? valid : (() => {
    const step = (valid.length - 1) / 24;
    return Array.from({ length: 25 }, (_, i) => valid[Math.round(i * step)]);
  })();

  const coords = sampled.map((s) => `${s.longitude},${s.latitude}`).join(";");
  try {
    const res = await fetch(
      `https://routing.openstreetmap.de/routed-car/route/v1/driving/${coords}?geometries=geojson&overview=full`,
      { signal: AbortSignal.timeout(10000) }
    );
    if (!res.ok) return null;
    const data = await res.json();
    if (data.code === "Ok" && data.routes?.[0]?.geometry?.coordinates) {
      return data.routes[0].geometry.coordinates.map(([lng, lat]) => [lat, lng]);
    }
  } catch {
    // timeout or network error — fall back to straight lines
  }
  return null;
}

/**
 * Returns the transit-only stops for a route, stripping any trailing Walk leg destination.
 * This ensures OSRM only routes along the bus path, not the final walk to destination.
 */
function getBusStops(route) {
  const legs = route.legs || [];
  const stops = route.stops || [];
  const lastLeg = legs[legs.length - 1];
  if (lastLeg && lastLeg.mode === "Walk" && stops.length > 1) {
    return stops.slice(0, stops.length - 1);
  }
  return stops;
}

/** Fetch OSRM geometry for all PT routes in parallel (bus portion only). */
async function fetchAllOsrmGeometries(routes) {
  return Promise.all((routes || []).map((r) => fetchOsrmGeometry(getBusStops(r))));
}

/**
 * Fetches a road-following walk polyline from OSRM (foot profile) between two points.
 * Returns [[lat,lng], ...] or null on failure.
 */
async function fetchOsrmFootLine(lat1, lng1, lat2, lng2) {
  const coords = `${lng1},${lat1};${lng2},${lat2}`;
  try {
    const res = await fetch(
      `https://routing.openstreetmap.de/routed-foot/route/v1/foot/${coords}?geometries=geojson&overview=full`,
      { signal: AbortSignal.timeout(10000) }
    );
    if (!res.ok) return null;
    const data = await res.json();
    if (data.code === "Ok" && data.routes?.[0]?.geometry?.coordinates) {
      return data.routes[0].geometry.coordinates.map(([lng, lat]) => [lat, lng]);
    }
  } catch {
    // timeout or network error — caller falls back to straight line
  }
  return null;
}

/**
 * For each PT route, fetches foot-profile OSRM lines for:
 *   - origin walk:  originPin → first bus stop (if pinLat/pinLng supplied)
 *   - dest walk:    last bus stop → destination stop (if route ends with a Walk leg)
 *   - destPinLine:  last stop → destination pin (if destPinLat/destPinLng supplied)
 * Returns an array of { originLine, destLine, destPinLine } (any may be null).
 */
async function fetchAllWalkGeometries(routes, pinLat, pinLng, destPinLat, destPinLng) {
  return Promise.all((routes || []).map(async (route) => {
    const allStops  = route.stops || [];
    const legs      = route.legs  || [];
    const lastLeg   = legs[legs.length - 1];
    const hasDestWalk = lastLeg && lastLeg.mode === "Walk" && allStops.length >= 2;
    const busStops  = hasDestWalk ? allStops.slice(0, allStops.length - 1) : allStops;

    let originLine  = null;
    let destLine    = null;
    let destPinLine = null;

    if (pinLat != null && busStops.length > 0) {
      const first = busStops[0];
      if (first.latitude != null) {
        originLine = await fetchOsrmFootLine(pinLat, pinLng, first.latitude, first.longitude);
      }
    }

    if (hasDestWalk) {
      const walkFrom = allStops[allStops.length - 2];
      const walkTo   = allStops[allStops.length - 1];
      if (walkFrom?.latitude != null && walkTo?.latitude != null) {
        destLine = await fetchOsrmFootLine(
          walkFrom.latitude, walkFrom.longitude,
          walkTo.latitude, walkTo.longitude
        );
      }
    }

    // Walk from the last stop in the route to the destination pin (if a dest pin was set)
    if (destPinLat != null && allStops.length > 0) {
      const lastStop = allStops[allStops.length - 1];
      if (lastStop?.latitude != null) {
        destPinLine = await fetchOsrmFootLine(
          lastStop.latitude, lastStop.longitude,
          destPinLat, destPinLng
        );
      }
    }

    return { originLine, destLine, destPinLine };
  }));
}

/**
 * Converts route options into Leaflet Polyline data with colours.
 *   Recommended PT  → green (solid)
 *   2nd PT          → amber (solid)
 *   Other PT        → red   (solid)
 *   Walk legs       → gray  (dashed)
 *   Car baseline    → blue  (solid)
 *
 * Walk segments (origin pin → first bus stop, last bus stop → destination)
 * are rendered as separate dashed gray lines so the user can see the
 * walking portions clearly, like Google Maps.
 */
function buildRouteLines(publicRoutes, carBaseline, geometries = [], carGeometry = null, originPin = null, walkGeometries = [], destinationPin = null) {
  const lines = [];
  // Non-recommended routes get amber → red → purple; never green (green = recommended only).
  const nonRecColors = ["#f59e0b", "#ef4444", "#8b5cf6"];
  const WALK_COLOR   = "#6b7788";
  const WALK_DASH    = "8 8";

  // Draw non-recommended routes first so the recommended one renders on top.
  const drawOrder = [
    ...publicRoutes.map((r, i) => ({ route: r, index: i })).filter(({ route }) => !route.recommended),
    ...publicRoutes.map((r, i) => ({ route: r, index: i })).filter(({ route }) => route.recommended),
  ];
  let nonRecIdx = 0;

  drawOrder.forEach(({ route, index }) => {
    const allStops    = route.stops || [];
    const legs        = route.legs  || [];
    const lastLeg     = legs[legs.length - 1];
    const hasDestWalk = lastLeg && lastLeg.mode === "Walk" && allStops.length >= 2;

    // ── Bus portion ──────────────────────────────────────────────────────────
    const busStops  = hasDestWalk ? allStops.slice(0, allStops.length - 1) : allStops;
    const fallback  = busStops
      .filter((s) => s.latitude != null && s.longitude != null)
      .map((s) => [s.latitude, s.longitude]);
    const positions  = geometries[index] || fallback;
    const routeColor = route.recommended ? "#22c55e" : (nonRecColors[nonRecIdx++] || "#ef4444");

    if (positions.length >= 2) {
      lines.push({ positions, color: routeColor, weight: route.recommended ? 6 : 4 });
    }

    const wg = walkGeometries[index] || {};

    // ── Walk: origin pin → first bus stop ────────────────────────────────────
    if (originPin && busStops.length > 0) {
      const first = busStops[0];
      if (first.latitude != null && first.longitude != null) {
        const walkPositions = wg.originLine ||
          [[originPin.lat, originPin.lng], [first.latitude, first.longitude]];
        lines.push({ positions: walkPositions, color: WALK_COLOR, weight: 3, dashArray: WALK_DASH });
      }
    }

    // ── Walk: last bus stop → destination stop (backend walk leg) ────────────
    if (hasDestWalk) {
      const walkFrom = allStops[allStops.length - 2];
      const walkTo   = allStops[allStops.length - 1];
      if (walkFrom?.latitude != null && walkTo?.latitude != null) {
        const walkPositions = wg.destLine ||
          [[walkFrom.latitude, walkFrom.longitude], [walkTo.latitude, walkTo.longitude]];
        lines.push({ positions: walkPositions, color: WALK_COLOR, weight: 3, dashArray: WALK_DASH });
      }
    }

    // ── Walk: last stop → destination pin ────────────────────────────────────
    if (destinationPin && allStops.length > 0) {
      const lastStop = allStops[allStops.length - 1];
      if (lastStop?.latitude != null) {
        const walkPositions = wg.destPinLine ||
          [[lastStop.latitude, lastStop.longitude], [destinationPin.lat, destinationPin.lng]];
        lines.push({ positions: walkPositions, color: WALK_COLOR, weight: 3, dashArray: WALK_DASH });
      }
    }
  });

  if (carBaseline) {
    const stops = carBaseline.stops || [];
    const fallback = stops
      .filter((s) => s.latitude != null && s.longitude != null)
      .map((s) => [s.latitude, s.longitude]);
    const positions = carGeometry || fallback;
    if (positions.length >= 2) {
      lines.push({ positions, color: "#3b82f6", weight: 3 });
    }
  }

  return lines;
}

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

function RouteCard({ route, carBaselineCo2Grams, onSelect }) {
  const durationSeconds = route.durationSeconds ?? route.totalDurationSeconds ?? 0;
  const durationMinutes = secondsToMins(durationSeconds);
  const co2Grams        = route.co2Grams || 0;
  const co2SavedGrams   = Math.max(0, carBaselineCo2Grams - co2Grams);
  const legs            = groupLegsByService(route.legs || []);
  const allStops        = route.stops || [];

  return (
    <div className={`route-card ${route.recommended ? "recommended" : ""}`}>
      {/* Header row: badge + key metrics */}
      <div className="route-header">
        <div className="route-header-left">
          {route.recommended
            ? <span className="badge">★ Recommended</span>
            : <span className="badge badge-secondary">Option</span>
          }
        </div>
        <div className="route-header-metrics">
          <span className="metric-chip"><strong>{durationMinutes}</strong> min</span>
          <span className="metric-chip">
            <strong>{route.transfers || 0}</strong> transfer{route.transfers !== 1 ? "s" : ""}
          </span>
          <span className="metric-chip metric-chip-co2">
            <strong>{formatCo2(co2Grams)}</strong> CO₂
          </span>
        </div>
      </div>

      {/* Recommendation reason */}
      {route.recommended && route.recommendationReason && (
        <div className="route-reason">{route.recommendationReason}</div>
      )}

      {/* Legs — one collapsible row per leg */}
      <div className="route-legs-list">
        {legs.length > 0 ? (
          legs.map((leg, i) => (
            <LegRow
              key={i}
              leg={leg}
              stops={getStopsForLeg(allStops, leg.fromStopName, leg.toStopName)}
              isLast={i === legs.length - 1}
            />
          ))
        ) : (
          <div className="route-no-legs">
            {route.modeSummary || formatRouteType(route.type)}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="route-footer">
        <span>CO₂ saved vs car: <strong>{formatCo2(co2SavedGrams)}</strong></span>
        {onSelect && (
          <button
            type="button"
            className="select-route-btn"
            onClick={() => onSelect(route)}
          >
            Select this route →
          </button>
        )}
      </div>
    </div>
  );
}

/** Returns the slice of allStops between fromName and toName (inclusive). */
function getStopsForLeg(allStops, fromName, toName) {
  const fromIdx = allStops.findIndex((s) => s.name === fromName);
  if (fromIdx === -1) return [];
  const toIdx = allStops.findIndex((s, i) => i > fromIdx && s.name === toName);
  if (toIdx === -1) return [];
  return allStops.slice(fromIdx, toIdx + 1);
}

/**
 * Groups consecutive legs with the same serviceName into one entry.
 * e.g. 20 individual Bus 405 segments become one group:
 *   { serviceName: "Bus 405", fromStopName: "Gleann Dara", toStopName: "Bohermore Cemetery",
 *     departureTime: "10:55", arrivalTime: "11:25" }
 */
function groupLegsByService(legs) {
  if (!legs || legs.length === 0) return [];
  const groups = [];
  let current = { ...legs[0] };

  for (let i = 1; i < legs.length; i++) {
    const leg = legs[i];
    if (leg.serviceName === current.serviceName) {
      // extend the current group — update end stop and arrival time
      current.toStopName = leg.toStopName;
      current.arrivalTime = leg.arrivalTime;
    } else {
      groups.push(current);
      current = { ...leg };
    }
  }
  groups.push(current);
  return groups;
}

/** A single collapsible leg row inside a RouteCard. */
function LegRow({ leg, stops, isLast }) {
  const [expanded, setExpanded] = useState(false);
  const stopCount = stops.length;

  return (
    <div className={`leg-row ${isLast ? "leg-row-last" : ""}`}>
      <button
        className="leg-row-header"
        type="button"
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
      >
        <span className="leg-service-chip">{leg.serviceName}</span>
        <span className="leg-route-text">
          {leg.fromStopName}
          <span className="leg-arrow-inline"> → </span>
          {leg.toStopName}
        </span>
        <span className="leg-time-text">
          {leg.departureTime} – {leg.arrivalTime}
        </span>
        {stopCount > 2 && (
          <span className="leg-stop-count">{stopCount} stops</span>
        )}
        <span className="leg-chevron">{expanded ? "▼" : "▶"}</span>
      </button>

      {expanded && stops.length > 0 && (
        <div className="leg-stops-list">
          {stops.map((stop, i) => (
            <div key={i} className="leg-stop-item">
              <span className={`leg-stop-dot ${i === 0 || i === stops.length - 1 ? "leg-stop-dot-end" : ""}`} />
              <span className="leg-stop-name">{stop.name}</span>
              {stop.departureTime && (
                <span className="leg-stop-time">{stop.departureTime}</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// The CarBaselineCard component renders a card for the baseline car route, displaying key information such as estimated drive time and CO₂ emissions.
//  This card is used for comparison purposes and is never auto-recommended.
function CarBaselineCard({ route }) {
  const durationSeconds = route.durationSeconds ?? route.totalDurationSeconds ?? 0;
  return (
    <div className="route-card" style={{ borderStyle: "dashed" }}>
      <div className="route-header">
        <div className="route-header-left">
          <span className="badge" style={{ background: "#6b7788" }}>🚗 Car baseline</span>
        </div>
        <div className="route-header-metrics">
          <span className="metric-chip"><strong>{secondsToMins(durationSeconds)}</strong> min</span>
          <span className="metric-chip">0 transfers</span>
          <span className="metric-chip" style={{ color: "#b45309", background: "rgba(180,90,0,0.08)" }}>
            <strong>{formatCo2(route.co2Grams || 0)}</strong> CO₂
          </span>
        </div>
      </div>
      <div className="route-footer" style={{ color: "#9aa3b0", fontStyle: "italic" }}>
        Comparison baseline only — not a recommended option.
      </div>
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
 * Routes the click to onPickOrigin or onPickDestination depending on mapMode.
 * Must be rendered inside a MapContainer.
 */
function MapClickHandler({ mapMode, onPickOrigin, onPickDestination }) {
  useMapEvents({
    click(e) {
      const coords = { lat: e.latlng.lat, lng: e.latlng.lng };
      if (mapMode === "destination") {
        onPickDestination(coords);
      } else {
        onPickOrigin(coords);
      }
    },
  });
  return null;
}

/**
 * Fits the map view to show all route lines whenever they change.
 */
function MapBoundsFitter({ routeLines }) {
  const map = useMap();
  useEffect(() => {
    if (!routeLines || routeLines.length === 0) return;
    const allPoints = routeLines.flatMap((r) => r.positions);
    if (allPoints.length === 0) return;
    map.fitBounds(allPoints, { padding: [30, 30] });
  }, [routeLines, map]);
  return null;
}

/**
 * Renders the map with an origin pin picker and, after a search,
 * coloured polylines for each route option.
 *
 * Route colours:
 *   green  (#22c55e) = recommended public transport
 *   amber  (#f59e0b) = second best public transport
 *   red    (#ef4444) = other public transport
 *   blue   (#3b82f6) = car baseline
 */
// Custom red marker icon for the destination pin.
const redIcon = new L.Icon({
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
  className: "leaflet-marker-red",
});

function OriginPickerMap({ originPin, destinationPin, mapMode, onPickOrigin, onPickDestination, routeLines }) {
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
      <MapClickHandler mapMode={mapMode} onPickOrigin={onPickOrigin} onPickDestination={onPickDestination} />
      {originPin && <Marker position={[originPin.lat, originPin.lng]} />}
      {destinationPin && <Marker position={[destinationPin.lat, destinationPin.lng]} icon={redIcon} />}

      {routeLines && routeLines.map((route, i) => (
        <Polyline
          key={i}
          positions={route.positions}
          pathOptions={{
            color: route.color,
            weight: route.weight || 5,
            opacity: 0.85,
            dashArray: route.dashArray || null,
          }}
        />
      ))}

      {routeLines && routeLines.length > 0 && (
        <MapBoundsFitter routeLines={routeLines} />
      )}
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
