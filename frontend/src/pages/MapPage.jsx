import { useEffect, useMemo, useState } from "react";
import FilterPanel from "../components/FilterPanel";
import MapView from "../components/MapView";
import BottomNav from "../components/BottomNav";
import { fetchStops, fetchDepartures } from "../services/mapApi";
import "../styles/map.css";

/**
 * Map page:
 * - holds UI state (location, selected modes, live/static)
 * - fetches stops for markers
 * - loads departures when a marker is clicked
 */
function MapPage({ activePage, onNavigate }) {
  const [location, setLocation] = useState("Galway");
  const [live, setLive] = useState(true);

  // Keep modes simple + aligned with your backend enum TransportMode
  const [selectedModes, setSelectedModes] = useState(["BUS"]);
  const [stops, setStops] = useState([]);
  const [error, setError] = useState(null);

  const [selectedStop, setSelectedStop] = useState(null);
  const [departures, setDepartures] = useState([]);
  const [loadingDepartures, setLoadingDepartures] = useState(false);

  // Load markers whenever filters change
  useEffect(() => {
    fetchStops({ location, modes: selectedModes, live })
      .then((data) => {
        setStops(data);
        setError(null);
      })
      .catch((err) => {
        setStops([]);
        setError("Backend not reachable");
      });
  }, [location, selectedModes, live]);

  async function handleStopClick(stop) {
    setSelectedStop(stop);
    setLoadingDepartures(true);

    try {
      const deps = await fetchDepartures(stop.id, live);
      setDepartures(deps);
    } catch {
      setDepartures([]);
    } finally {
      setLoadingDepartures(false);
    }
  }

  // Ireland map centers: Galway, Dublin, Cork
  const locationCenters = {
    Galway: [53.2740, -9.0498],
    Dublin: [53.3498, -6.2603],
    Cork: [51.8985, -8.4756],
  };

  const mapCenter = useMemo(() => {
    return locationCenters[location] || locationCenters.Galway;
  }, [location]);

  return (
    <div className="map-page">
      <div className="map-topbar">
        <div className="map-title">Map View</div>

        <div className="map-location">
          Location:&nbsp;
          <select value={location} onChange={(e) => setLocation(e.target.value)}>
            <option value="Galway">Galway</option>
            <option value="Dublin">Dublin</option>
            <option value="Cork">Cork</option>
          </select>
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="map-content">
        <FilterPanel
          selectedModes={selectedModes}
          onChangeModes={setSelectedModes}
          live={live}
          onChangeLive={setLive}
        />

        <MapView
          center={mapCenter}
          stops={stops}
          selectedStop={selectedStop}
          departures={departures}
          loadingDepartures={loadingDepartures}
          onStopClick={handleStopClick}
          onClosePopup={() => setSelectedStop(null)}
        />
      </div>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default MapPage;
