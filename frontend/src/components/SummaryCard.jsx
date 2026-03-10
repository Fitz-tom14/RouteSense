// SummaryCard.jsx -
// React component to display a summary of the selected journey, comparing the CO₂ emissions of the public transport route to a baseline car journey. Shows a simple bar chart and percentage saved.
function formatCo2(grams) {
  const g = Math.max(0, grams || 0);
  return g >= 1000 ? `${(g / 1000).toFixed(1)} kg` : `${Math.round(g)} g`;
}

// Props:
// - journey: the selected journey object containing details like co2Grams and transfers
// - carBaselineCo2Grams: the estimated CO₂ emissions for the same journey if taken by car, used for comparison
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

  // Calculates the CO₂ emissions for the selected public transport route and the baseline car journey, then computes the percentage of CO₂ saved by taking public transport instead of driving. Displays this information in a simple bar chart format, along with the exact CO₂ values and the number of transfers if available.
  const ptCo2 = journey.co2Grams || 0;
  const carCo2 = carBaselineCo2Grams || 0;
  const co2Saved = Math.max(0, carCo2 - ptCo2);
  const pctSaved = carCo2 > 0 ? Math.round((co2Saved / carCo2) * 100) : 0;
  const maxCo2 = Math.max(carCo2, ptCo2, 1);

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
          <div style={{ fontSize: 12, color: '#6b7788', marginTop: 6 }}>
            {journey.transfers} transfer{journey.transfers !== 1 ? 's' : ''}
          </div>
        )}
      </div>
    </div>
  );
}

export default SummaryCard;
