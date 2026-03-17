// Map view component for the RouteSense app, using React Leaflet to display stops and departures on a map.
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

/**
 * Leaflet default icon fix (common gotcha in Vite).
 */
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png", // use unpkg CDN for marker icons
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png", // use unpkg CDN for marker icons
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png" // use unpkg CDN for marker shadow
});

/**
 * Map view:
 * - displays stops as markers
 * - shows a popup with departures when selectedStop is set
 */
function MapView({
  center, // [latitude, longitude] for map center
  stops, // list of stops to display as markers
  selectedStop, // currently selected stop (object with id, name, latitude, longitude) 
  departures,// list of departures for the selected stop (objects with routeName and minutes until departure)
  loadingDepartures,// boolean indicating if departures are currently being loaded
  onStopClick,// callback function to call when a stop marker is clicked, receives the stop object as argument
  onClosePopup // callback function to call when the popup is closed
}) {

  // Render the map with markers for each stop. When a marker is clicked, it sets the selected stop and shows a popup with departure information.
  return (
    <div className="map-wrapper">
      <MapContainer center={center} zoom={13} className="leaflet-map">
        <TileLayer
          attribution='&copy; OpenStreetMap contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />


        {stops.map((s) => ( // Add a marker for each stop. When clicked, it will call onStopClick with the stop's information.
          <Marker
            key={s.id}
            position={[s.latitude, s.longitude]}
            eventHandlers={{
              click: () => onStopClick(s)
            }}
          >
            {selectedStop && selectedStop.id === s.id && ( // Show a popup if this stop is the currently selected stop
              <Popup onClose={onClosePopup}>
                <div className="popup-title">{selectedStop.name}</div>
                <div className="popup-subtitle">Next Departures</div>

                {loadingDepartures ? ( // Show loading state while departures are being fetched
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
