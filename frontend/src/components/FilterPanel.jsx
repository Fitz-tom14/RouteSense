// FilterPanel.jsx - React component for the filter panel on the left side of the app, allowing users to select transport modes and toggle live/static schedule. Also includes a legend for the map markers.
const MODE_COLOR = {
  BUS:   "#3b82f6",
  TRAIN: "#f59e0b",
};

// Props:
// - selectedModes: array of currently selected transport modes (e.g. ["BUS", "TRAIN"])
// - onChangeModes: callback function to update selected modes in parent component
// - live: boolean indicating whether live schedule is enabled
// - onChangeLive: callback function to toggle live schedule in parent component
function FilterPanel({ selectedModes, onChangeModes }) {
  const modes = [
    { key: "BUS",   label: "Bus" },
    { key: "TRAIN", label: "Train" },
  ];

  // Toggles a transport mode on or off when the corresponding button is clicked.
  function toggleMode(key) {
    if (selectedModes.includes(key)) {
      onChangeModes(selectedModes.filter((m) => m !== key));
    } else {
      onChangeModes([...selectedModes, key]);
    }
  }

  // Determines the button styles based on whether the mode is selected or not, and applies the corresponding color from MODE_COLOR.
  // The live/static toggle is a simple switch that updates the 'live' state in the parent component, and the legend shows the color coding for different transport modes on the map.
  return (
    <aside className="filters">
      {/* Mode filter */}
      <div className="filter-card">
        <div className="filter-card-title">Transport Mode</div>
        <div className="mode-buttons">
          {modes.map((m) => (
            <button
              key={m.key}
              className={`mode-btn ${selectedModes.includes(m.key) ? "active" : ""}`}
              onClick={() => toggleMode(m.key)}
            >
              <span
                className="mode-btn-dot"
                style={{ background: MODE_COLOR[m.key] }}
              />
              {m.label}
            </button>
          ))}
        </div>
      </div>

      {/* Legend */}
      <div className="filter-card">
        <div className="filter-card-title">Legend</div>
        <div className="legend-item">
          <span className="legend-dot" style={{ background: "#3b82f6" }} />
          Bus stop
        </div>
        <div className="legend-item">
          <span className="legend-dot" style={{ background: "#f59e0b" }} />
          Train station
        </div>
      </div>

      {/* Tip */}
      <div className="filter-card">
        <div className="filter-card-title">How to use</div>
        <p style={{ fontSize: "12px", color: "#6b7280", lineHeight: "1.5", margin: 0 }}>
          Tap any stop on the map to see the next 5 scheduled departures.
        </p>
      </div>
    </aside>
  );
}

export default FilterPanel;
