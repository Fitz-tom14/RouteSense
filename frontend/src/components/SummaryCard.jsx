// Compares the selected route's CO₂ against the car baseline that comes back from the API.
function formatCo2(grams) {
  const g = Math.max(0, grams || 0);
  return g >= 1000 ? `${(g / 1000).toFixed(1)} kg` : `${Math.round(g)} g`;
}

function SummaryCard({ journey, carBaselineCo2Grams }) {
  if (!journey) {
    return (
      <div className="card summary-card">
        <h3 className="card-title">Journey Summary</h3>
        <p className="card-subtitle">CO₂ Comparison by Mode</p>
        <div className="chart-placeholder">
          <span>Select a route to see your summary</span>
        </div>
      </div>
    );
  }

  // Bar widths are percentages of the highest CO₂ value — no chart library needed, just CSS width
  const ptCo2 = journey.co2Grams || 0;
  const carCo2 = carBaselineCo2Grams || 0;
  const co2Saved = Math.max(0, carCo2 - ptCo2);                              // how many grams saved vs driving
  const pctSaved = carCo2 > 0 ? Math.round((co2Saved / carCo2) * 100) : 0;  // percentage saved, shown as "X% less CO₂"
  const maxCo2 = Math.max(carCo2, ptCo2, 1); // the 1 prevents division by zero if both values happen to be 0

  return (
    <div className="card summary-card">
      <h3 className="card-title">Journey Summary</h3>
      <p className="card-subtitle">CO₂ vs Driving</p>

      <div style={{ marginTop: 14 }}>
        <div style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 12, marginBottom: 4, color: '#6b7788' }}>Your route</div>
          <div style={{ background: '#e5e7eb', borderRadius: 4, height: 10 }}>
            <div style={{ width: `${(ptCo2 / maxCo2) * 100}%`, background: '#22c55e', height: '100%', borderRadius: 4, minWidth: ptCo2 > 0 ? 4 : 0 }} />
          </div>
          <div style={{ fontSize: 11, marginTop: 3, color: '#374151' }}>{formatCo2(ptCo2)}</div>
        </div>

        <div style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 12, marginBottom: 4, color: '#6b7788' }}>By car</div>
          <div style={{ background: '#e5e7eb', borderRadius: 4, height: 10 }}>
            <div style={{ width: `${(carCo2 / maxCo2) * 100}%`, background: '#ef4444', height: '100%', borderRadius: 4 }} />
          </div>
          <div style={{ fontSize: 11, marginTop: 3, color: '#374151' }}>{formatCo2(carCo2)}</div>
        </div>

        {pctSaved > 0 && (
          <div style={{ fontSize: 13, color: '#16a34a', fontWeight: 600, marginTop: 10 }}>
            {pctSaved}% less CO₂ than driving
          </div>
        )}

        {journey.transfers !== undefined && (
          // !== 1 ? 's' : '' handles pluralisation — "1 transfer" vs "2 transfers"
          <div style={{ fontSize: 12, color: '#6b7788', marginTop: 6 }}>
            {journey.transfers} transfer{journey.transfers !== 1 ? 's' : ''}
          </div>
        )}
      </div>
    </div>
  );
}

export default SummaryCard;
