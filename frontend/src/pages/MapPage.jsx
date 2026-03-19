import { useEffect, useState } from "react";
import { MapContainer, TileLayer, CircleMarker, Popup } from "react-leaflet";
import FilterPanel from "../components/FilterPanel";
import BottomNav from "../components/BottomNav";
import { fetchStops, fetchDepartures } from "../services/mapApi";
import "../styles/map.css";

// MODE_COLOR defines the colors used for different transport modes on the map and in the filter panel.
const MODE_COLOR = {
  BUS:   "#3b82f6",
  TRAIN: "#f59e0b",
};

// StopMarker component represents a single stop on the map, showing a popup with upcoming departures when clicked.
function StopMarker({ stop, live }) {
  const [departures, setDepartures] = useState(null);
  const [loading, setLoading]       = useState(false);

  // When the popup is opened, if departures haven't been loaded yet, it fetches the upcoming departures for that stop using the fetchDepartures function. It shows a loading state while fetching, and handles errors by setting departures to an empty array. The popup displays the stop name, mode badge, and a list of upcoming departures with route names and minutes until departure.
  async function handlePopupOpen() {
    if (departures !== null) return;
    setLoading(true);
    try {
      const data = await fetchDepartures(stop.id, live);
      setDepartures(Array.isArray(data) ? data : []);
    } catch {
      setDepartures([]);
    } finally {
      setLoading(false);
    }
  }

  const color = MODE_COLOR[stop.mode] || "#3b82f6";

  // Renders a CircleMarker for the stop location, with a Popup that shows the stop name, mode badge, and upcoming departures when opened. The marker color corresponds to the transport mode, and the popup content updates based on the loading state and fetched departure data.
  return (
    <CircleMarker
      center={[stop.latitude, stop.longitude]}
      radius={6}
      pathOptions={{ color, fillColor: color, fillOpacity: 0.85, weight: 1.5 }}
      eventHandlers={{ popupopen: handlePopupOpen }}
    >
      <Popup>
        <div className="popup-stop-name">{stop.name}</div>
        <span
          className="popup-mode-badge"
          style={{ background: color }}
        >
          {stop.mode}
        </span>

        {loading && <div className="popup-loading">Loading departures…</div>}

        {!loading && departures !== null && departures.length === 0 && (
          <div className="popup-empty">No upcoming departures</div>
        )}

        {!loading && departures && departures.map((d, i) => (
          <div key={i} className="popup-departure-row">
            <span className="popup-route-name">{d.routeName}</span>
            <span className="popup-dep-time">{d.scheduledTime}</span>
            <span className="popup-minutes">{d.minutes === 0 ? "Due" : `${d.minutes} min`}</span>
          </div>
        ))}
      </Popup>
    </CircleMarker>
  );
}

// MapPage component displays an interactive map with public transport stops based on the selected location and filters.
// It includes a top bar for location selection, a filter panel for transport modes and live/static toggle, and a bottom navigation for switching between pages. The map shows stops as colored markers, and clicking on a marker opens a popup with stop details and upcoming departures.
function MapPage({ activePage, onNavigate }) {
  const [location, setLocation]           = useState("Galway");
  const live = true;
  const [selectedModes, setSelectedModes] = useState(["BUS"]);
  const [stops, setStops]                 = useState([]);
  const [loadingStops, setLoadingStops]   = useState(false);

  const cityCenter = {
    Galway: [53.2707, -9.0568],
    Dublin: [53.3498, -6.2603],
    Cork:   [51.8985, -8.4756],
  };

  useEffect(() => {
    setLoadingStops(true);
    fetchStops({ location, modes: selectedModes, live })
      .then((data) => setStops(Array.isArray(data) ? data : []))
      .catch(() => setStops([]))
      .finally(() => setLoadingStops(false));
  }, [location, selectedModes, live]);

  return (
    <div className="map-page">
      {/* ── Top bar ── */}
      <div className="map-topbar">
        <div className="map-title">Map View</div>
        <div className="map-topbar-spacer" />
        <div className="map-location">
          <select value={location} onChange={(e) => setLocation(e.target.value)}>
            <option value="Galway">Galway</option>
            <option value="Dublin">Dublin</option>
            <option value="Cork">Cork</option>
          </select>
        </div>
        {loadingStops ? (
          <span className="map-stop-count">Loading…</span>
        ) : (
          <span className="map-stop-count">{stops.length} stops</span>
        )}
      </div>

      {/* ── Content ── */}
      <div className="map-content">
        <FilterPanel
          selectedModes={selectedModes}
          onChangeModes={setSelectedModes}
        />

        <div className="map-wrapper">
          <MapContainer
            key={location}
            center={cityCenter[location] || cityCenter.Galway}
            zoom={13}
            zoomControl={true}
            className="leaflet-map"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {stops.map((stop) => (
              <StopMarker key={stop.id} stop={stop} live={live} />
            ))}
          </MapContainer>
        </div>
      </div>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default MapPage;
