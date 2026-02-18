import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

/**
 * Leaflet default icon fix (common gotcha in Vite).
 */
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png"
});

/**
 * Map view:
 * - displays stops as markers
 * - shows a popup with departures when selectedStop is set
 */
function MapView({
  center,
  stops,
  selectedStop,
  departures,
  loadingDepartures,
  onStopClick,
  onClosePopup
}) {
  return (
    <div className="map-wrapper">
      <MapContainer center={center} zoom={13} className="leaflet-map">
        <TileLayer
          attribution='&copy; OpenStreetMap contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {stops.map((s) => (
          <Marker
            key={s.id}
            position={[s.latitude, s.longitude]}
            eventHandlers={{
              click: () => onStopClick(s)
            }}
          >
            {selectedStop && selectedStop.id === s.id && (
              <Popup onClose={onClosePopup}>
                <div className="popup-title">{selectedStop.name}</div>
                <div className="popup-subtitle">Next Departures</div>

                {loadingDepartures ? (
                  <div>Loading…</div>
                ) : departures.length === 0 ? (
                  <div>No departures available</div>
                ) : (
                  <ul className="departure-list">
                    {departures.map((d, idx) => (
                      <li key={idx} className="departure-item">
                        <span>{d.routeName}</span>
                        <span>{d.minutes} min</span>
                      </li>
                    ))}
                  </ul>
                )}
              </Popup>
            )}
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}

export default MapView;
