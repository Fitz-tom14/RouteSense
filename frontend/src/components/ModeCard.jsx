// Groups the journey legs by mode and shows a count for each — e.g. "Bus × 2, Walk × 1".
const MODE_ICONS = { Bus: '🚌', Train: '🚆', Walk: '🚶', Bike: '🚲' };

function ModeCard({ legs }) {
  const modeCounts = {};
  (legs || []).forEach((leg) => {
    const mode = leg.mode || 'Bus';
    modeCounts[mode] = (modeCounts[mode] || 0) + 1;
  });

  const entries = Object.entries(modeCounts);
  const hasData = entries.length > 0;

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
