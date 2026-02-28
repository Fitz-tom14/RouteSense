import { useState } from "react";
import { MapContainer, TileLayer } from "react-leaflet";
import FilterPanel from "../components/FilterPanel";
import BottomNav from "../components/BottomNav";
import "../styles/map.css";

/**
 * Map page:
 * - holds UI state (location, selected modes, live/static)
 * - renders the Leaflet map beside the filter panel
 */
function MapPage({ activePage, onNavigate }) {
  const [location, setLocation] = useState("Galway");
  const [live, setLive] = useState(true);
  const [selectedModes, setSelectedModes] = useState(["BUS"]);
  const galwayCenter = [53.2707, -9.0568];

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

      <div className="map-content">
        <FilterPanel
          selectedModes={selectedModes}
          onChangeModes={setSelectedModes}
          live={live}
          onChangeLive={setLive}
        />

        <div className="map-wrapper">
          <MapContainer
            center={galwayCenter}
            zoom={13}
            zoomControl={true}
            className="leaflet-map"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
          </MapContainer>
        </div>
      </div>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default MapPage;
