// ModeCard.jsx -
// React component to display the transport modes used in the selected route, showing the count of legs for each mode. Uses emojis for visual representation of modes.
const MODE_ICONS = { Bus: '🚌', Train: '🚆', Walk: '🚶', Bike: '🚲' };

// Props:
// - legs: array of leg objects from the selected journey, where each leg has a 'mode' property indicating the transport mode used (e.g. "Bus", "Train").
function ModeCard({ legs }) {
  const modeCounts = {};
  (legs || []).forEach((leg) => {
    const mode = leg.mode || 'Bus';
    modeCounts[mode] = (modeCounts[mode] || 0) + 1;
  });

  const entries = Object.entries(modeCounts);
  const hasData = entries.length > 0;

  // Displays the transport modes used in the selected route, showing the count of legs for each mode.
  // If no route is selected, prompts the user to select a route to see the modes.
  return (
    <div className="card mode-card">
      <h3 className="card-title">Transport Mode Used</h3>
      {hasData ? (
        <div className="mode-items">
          {entries.map(([mode, count]) => (
            <div key={mode} className="mode-item">
              <div className="mode-name">{MODE_ICONS[mode] || '🚌'} {mode}</div>
              <div className="mode-percentage">{count} leg{count !== 1 ? 's' : ''}</div>
            </div>
          ))}
        </div>
      ) : (
        <p className="card-subtitle" style={{ marginTop: 12 }}>Select a route to see modes</p>
      )}
    </div>
  );
}

export default ModeCard;
