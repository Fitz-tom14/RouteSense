// Main journey search page — user types or pins a start and end point, hits Go, and sees ranked route options with CO₂ comparison.

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

// Builds the JSON body for the POST /api/journeys/search request.
// Uses stop IDs when the user typed a stop name, or lat/lng when they dropped a map pin.
// Also converts the "arrive by" time string to seconds-since-midnight if set.
function buildSearchRequestBody(originStop, originPin, destinationStop, destinationPin, arriveByTime) {
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

  return body;
}

// Parses the backend journey search response.
// The backend can return either a plain array or an object with an "options" array.
// Separates public transport options from the car baseline and extracts the car CO2 figure.
function parseJourneyResponse(data) {
  // Backend can return a plain array or an object with an "options" key — handle both
  const options = Array.isArray(data) ? data : data?.options;
  const normalizedOptions = Array.isArray(options) ? options : [];

  // Split out the CAR_BASELINE option so it can be displayed separately from the public transport results
  const carOption =
    normalizedOptions.find((option) => option?.type === "CAR_BASELINE") || null;
  const ptOptions = normalizedOptions.filter(
    (option) => option?.type !== "CAR_BASELINE"
  );

  const carBaselineCo2 =
    typeof data?.carBaselineCo2Grams === "number"
      ? data.carBaselineCo2Grams
      : carOption?.co2Grams || 0;

  return { ptOptions, carOption, carBaselineCo2 };
}

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

  // Shared helper used by both origin and destination inputs — keeps the autocomplete logic in one place
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

  // Debounce — waits 250ms after the user stops typing before hitting the backend, avoids a request on every keystroke
  function onOriginChange(value) {
    setOriginText(value);
    setOriginStop(null);
    setOriginPin(null); // typing clears any map pin

    if (originDebounceRef.current) {
      clearTimeout(originDebounceRef.current);
    }

    originDebounceRef.current = setTimeout(() => {
      fetchStopSuggestions(value, setOriginOptions, setLoadingOrigin);
    }, 250);
  }

  // User dropped a pin on the map instead of typing — store the coords and clear the text input
  function handleMapPick(coords) {
    setOriginPin(coords);
    setOriginStop(null);
    setOriginText("");
    setOriginOptions([]);
  }

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

  // Same debounce pattern as origin
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

  // User tapped a suggestion from the dropdown — lock it in and close the list
  function pickOrigin(stop) {
    setOriginStop(stop);
    setOriginText(stop.name || stop.id);
    setOriginOptions([]);
  }

  function pickDestination(stop) {
    setDestinationStop(stop);
    setDestinationText(stop.name || stop.id);
    setDestinationOptions([]);
  }

  // Triggered when user hits Go — validates that both origin and destination are set, then calls the backend
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
      const body = buildSearchRequestBody(originStop, originPin, destinationStop, destinationPin, arriveByTime);

      // The backend is expected to return a JSON response containing an array of journey options, each with details like duration, CO₂ emissions, and transport mode, as well as a car baseline option for comparison. We parse the response and update the state with the new routes and baseline data, or show an error message if the request fails.
      const res = await fetch(`${BACKEND}/api/journeys/search`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      const { ptOptions, carOption, carBaselineCo2 } = parseJourneyResponse(data);

      setPublicRoutes(ptOptions);
      setCarBaseline(carOption);
      setCarBaselineCo2Grams(carBaselineCo2);
      setRouteGeometries([]);
      setCarGeometry(null);
      setWalkGeometries([]);
      setError("");

      // Car baseline geometry comes directly from ORS via the backend (reliable, no external call needed).
      // Fall back to OSRM only if the backend didn't return it (e.g. no ORS key configured).
      if (data?.carRouteGeometry && Array.isArray(data.carRouteGeometry)) {
        setCarGeometry(data.carRouteGeometry);
      } else if (carOption) {
        fetchOsrmGeometry(carOption.stops, "car").then(setCarGeometry);
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
                  mapColor={MAP_ROUTE_COLORS[index] ?? null}
                  onSelect={onSelectJourney ? (r) => {
                    const lastLeg = r.legs?.findLast?.(l => l.mode !== "Walk") ?? r.legs?.[r.legs.length - 1];
                    const dest = destinationText || destinationStop?.name || lastLeg?.toStopName;
                    onSelectJourney(r, carBaselineCo2Grams, dest);
                  } : undefined}
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
            {hasSearched && publicRoutes.length > 0 && (
              <div className="map-legend">
                <span className="map-legend-item">
                  <span className="map-legend-dot" style={{ background: "#22c55e" }} /> Best
                </span>
                {publicRoutes.length > 1 && (
                  <span className="map-legend-item">
                    <span className="map-legend-dot" style={{ background: "#eab308" }} /> 2nd
                  </span>
                )}
                {publicRoutes.length > 2 && (
                  <span className="map-legend-item">
                    <span className="map-legend-dot" style={{ background: "#ef4444" }} /> 3rd
                  </span>
                )}
                {carBaseline && (
                  <span className="map-legend-item">
                    <span className="map-legend-dot" style={{ background: "#3b82f6" }} /> Car
                  </span>
                )}
                <span className="map-legend-item">
                  <span className="map-legend-walk" /> Walking
                </span>
              </div>
            )}
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

// OriginPickerMap component renders a Leaflet map that allows users to pick origin and destination points by clicking on the map.
// It displays markers for the selected origin and destination pins, and draws polylines for the public transport routes and car baseline if available. The map view automatically fits to show all relevant points and routes when they change.
// profile: "foot" for bus/transit routes, "car" for the car baseline
async function fetchOsrmGeometry(stops, profile = "foot") {
  const valid = (stops || []).filter((s) => s.latitude != null && s.longitude != null);
  if (valid.length < 2) return null;

  // Subsample to at most 25 waypoints to stay within OSRM limits
  const sampled = valid.length <= 25 ? valid : (() => {
    const step = (valid.length - 1) / 24;
    return Array.from({ length: 25 }, (_, i) => valid[Math.round(i * step)]);
  })();

  const coords = sampled.map((s) => `${s.longitude},${s.latitude}`).join(";");
  const service = profile === "car" ? "routed-car/route/v1/driving" : "routed-foot/route/v1/foot";
  try {
    const res = await fetch(
      `https://routing.openstreetmap.de/${service}/${coords}?geometries=geojson&overview=full`,
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

// Returns only the stops that are boarding/alighting points of transit (non-Walk) legs.
// Skipping footpath walk-leg stops prevents OSRM from routing through transfer stops
// that may be in the wrong direction, which would cause the route to appear to go backwards.
// stops[i] is the from-stop of legs[i], and to-stop of legs[i-1].
function getBusStops(route) {
  const legs = route.legs || [];
  const stops = route.stops || [];

  const transitIndices = new Set();
  legs.forEach((leg, i) => {
    if (leg.mode !== "Walk") {
      transitIndices.add(i);
      transitIndices.add(i + 1);
    }
  });

  if (transitIndices.size === 0) return stops;
  const result = stops.filter((_, i) => transitIndices.has(i));
  return result.length >= 2 ? result : stops;
}


// For each public transport route, this function fetches road-following geometries from OSRM for the transit legs.
// It handles different cases for where to get the geometry based on the presence of GTFS shape points and whether the route is train-only, falling back to straight lines between stops if necessary.
//  The result is an array of geometries corresponding to each route, which can be used for accurate mapping of the routes on the frontend.
async function fetchAllOsrmGeometries(routes) {
  return Promise.all((routes || []).map((r) => {
    const legs = r.legs || [];
    const transitLegs = legs.filter((l) => l.mode !== "Walk");

    // Use GTFS shape points if all transit legs have them
    const hasShapes = transitLegs.length > 0 &&
      transitLegs.every((l) => l.shapePoints && l.shapePoints.length >= 2);
    if (hasShapes) {
      const points = [];
      for (const leg of transitLegs) {
        points.push(...leg.shapePoints.map((p) => [p[0], p[1]]));
      }
      return Promise.resolve(points.length >= 2 ? points : null);
    }

    return fetchOsrmGeometry(getBusStops(r));
  }));
}

// Fetches foot-profile OSRM lines for the walk legs of each route, including the walk from the origin pin to the first bus stop, the walk from the last bus stop to the destination (if the route ends with a walk leg), and the walk from the last bus stop to the destination pin (if a destination pin is set). This allows us to show accurate walking paths on the map for these segments, which may not follow roads and thus require foot routing.
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

// For each route, we fetch the geometries for the walk legs (origin pin → first stop, last stop → destination leg, last stop → destination pin) as applicable.
// This is done in parallel for all routes to optimize loading times, and the results are stored in an array corresponding to each route.
// The frontend can then use these geometries to render accurate walking paths on the map for these segments.
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

// Top 3 map colors: green = best, yellow = 2nd, red = 3rd. Blue = car baseline.
const MAP_ROUTE_COLORS = ["#22c55e", "#eab308", "#ef4444"];

// Builds an array of route lines to be rendered on the map.
// Only the top 3 public transport routes are shown (green/yellow/red) plus the car baseline (blue).
// Walk legs are drawn as dashed grey lines for all 3 shown routes.
function buildRouteLines(publicRoutes, carBaseline, geometries = [], carGeometry = null, originPin = null, walkGeometries = [], destinationPin = null) {
  const lines = [];
  const WALK_COLOR = "#6b7788";
  const WALK_DASH  = "8 8";

  // Only draw the top 3 routes; draw in reverse so green (index 0) renders on top.
  const topRoutes = publicRoutes.slice(0, 3);
  [...topRoutes.map((r, i) => ({ route: r, index: i }))].reverse().forEach(({ route, index }) => {
    const allStops    = route.stops || [];
    const legs        = route.legs  || [];
    const lastLeg     = legs[legs.length - 1];
    const hasDestWalk = lastLeg && lastLeg.mode === "Walk" && allStops.length >= 2;

    const busStops = hasDestWalk ? allStops.slice(0, allStops.length - 1) : allStops;
    const fallback = busStops
      .filter((s) => s.latitude != null && s.longitude != null)
      .map((s) => [s.latitude, s.longitude]);
    const positions  = geometries[index] || fallback;
    const routeColor = MAP_ROUTE_COLORS[index];
    const weight     = index === 0 ? 6 : 4;

    if (positions.length >= 2) {
      lines.push({ positions, color: routeColor, weight });
    }

    const wg = walkGeometries[index] || {};

    // Walk: origin pin → first bus stop
    if (originPin && busStops.length > 0) {
      const first = busStops[0];
      if (first.latitude != null && first.longitude != null) {
        const walkPositions = wg.originLine ||
          [[originPin.lat, originPin.lng], [first.latitude, first.longitude]];
        lines.push({ positions: walkPositions, color: WALK_COLOR, weight: 3, dashArray: WALK_DASH });
      }
    }

    // Walk: last bus stop → destination stop (backend walk leg)
    if (hasDestWalk) {
      const walkFrom = allStops[allStops.length - 2];
      const walkTo   = allStops[allStops.length - 1];
      if (walkFrom?.latitude != null && walkTo?.latitude != null) {
        const walkPositions = wg.destLine ||
          [[walkFrom.latitude, walkFrom.longitude], [walkTo.latitude, walkTo.longitude]];
        lines.push({ positions: walkPositions, color: WALK_COLOR, weight: 3, dashArray: WALK_DASH });
      }
    }

    // Walk: last stop → destination pin
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

function RouteCard({ route, carBaselineCo2Grams, onSelect, mapColor }) {
  const durationSeconds = route.durationSeconds ?? route.totalDurationSeconds ?? 0;
  const durationMinutes = secondsToMins(durationSeconds);
  const co2Grams        = route.co2Grams || 0;
  const co2SavedGrams   = Math.max(0, carBaselineCo2Grams - co2Grams);
  const legs            = groupLegsByService(route.legs || []);
  const allStops        = route.stops || [];

  return (
    <div
      className={`route-card ${route.recommended ? "recommended" : ""}`}
      style={mapColor ? { borderLeftColor: mapColor, borderLeftWidth: 4 } : {}}
    >
      {/* Header row: badge + key metrics */}
      <div className="route-header">
        <div className="route-header-left">
          {mapColor && <span className="route-map-dot" style={{ background: mapColor }} />}
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

// Returns the slice of allStops between fromName and toName (inclusive). 
function getStopsForLeg(allStops, fromName, toName) {
  const fromIdx = allStops.findIndex((s) => s.name === fromName);
  if (fromIdx === -1) return [];
  const toIdx = allStops.findIndex((s, i) => i > fromIdx && s.name === toName);
  if (toIdx === -1) return [];
  return allStops.slice(fromIdx, toIdx + 1);
}

// Groups consecutive legs that share the same service name into a single leg, extending the toStopName and arrivalTime of the group as needed.
// This simplifies the route display by consolidating segments that are part of the same transit service, making it easier for users to understand the route structure at a glance.
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


// The ComparisonPanel component renders a horizontal bar chart comparing the duration and CO₂ emissions of all route options, including the car baseline if available.
// Each option is represented by a bar whose length corresponds to its duration relative to the longest option, and the color indicates whether it is recommended or not.
// The panel also includes a legend to explain the colors used for recommended and other options.
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

// The MapClickHandler component listens for click events on the map and calls the appropriate callback (onPickOrigin or onPickDestination) with the clicked coordinates, depending on the current map mode (origin or destination).
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

// The MapBoundsFitter component adjusts the map view to fit all the route lines whenever they change, ensuring that the user can see the entire route on the map without needing to manually zoom or pan.
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
