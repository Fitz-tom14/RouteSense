/**
 * Filter panel:
 * - mode selection (BUS/TRAIN/TRAM)
 * - live/static toggle
 *
 * Kept dumb/simple: receives state + callbacks from the page.
 */
function FilterPanel({ selectedModes, onChangeModes, live, onChangeLive }) {
  const modes = [
    { key: "BUS", label: "Bus" },
    { key: "TRAIN", label: "Train" },
    { key: "TRAM", label: "Tram" }
  ];

  function toggleMode(modeKey) {
    if (selectedModes.includes(modeKey)) {
      onChangeModes(selectedModes.filter((m) => m !== modeKey));
    } else {
      onChangeModes([...selectedModes, modeKey]);
    }
  }

  return (
    <aside className="filters">
      <div className="filters-header">Filters</div>

      <div className="mode-buttons">
        {modes.map((m) => (
          <button
            key={m.key}
            className={`mode-btn ${selectedModes.includes(m.key) ? "active" : ""}`}
            onClick={() => toggleMode(m.key)}
          >
            {m.label}
          </button>
        ))}
      </div>

      <div className="live-toggle">
        <span>Live</span>
        <label className="switch">
          <input type="checkbox" checked={live} onChange={(e) => onChangeLive(e.target.checked)} />
          <span className="slider" />
        </label>
        <span>Static</span>
      </div>

      <div className="selected-modes">
        <div className="selected-title">Selected Modes</div>
        <ul>
          {selectedModes.length === 0 ? <li>None</li> : selectedModes.map((m) => <li key={m}>✓ {m}</li>)}
        </ul>
      </div>
    </aside>
  );
}

export default FilterPanel;
