import { useState, useEffect } from "react";
import TopBar from "../components/TopBar";
import BottomNav from "../components/BottomNav";
import { logout } from "../services/auth";
import { loadHistory } from "../services/history";
import "../styles/history.css";

// Build array of date strings (YYYY-MM-DD) for the last 7 or 30 days
function getDays(period) {
  const count = period === "week" ? 7 : 30;
  const days = [];
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push(d.toISOString().split("T")[0]);
  }
  return days;
}

// Simplify a modeSummary into "Bus", "Train", or "Walk"
function simplifyMode(modeSummary) {
  const s = modeSummary || "";
  if (s.toLowerCase().includes("train")) return "Train";
  if (s.toLowerCase().includes("bus"))   return "Bus";
  return "Walk";
}

// Short axis label: day-of-week for week view, day number for month view
function shortLabel(dateStr, period) {
  const d = new Date(dateStr + "T00:00:00");
  return period === "week"
    ? ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][d.getDay()]
    : String(d.getDate());
}

function HistoryPage({ activePage, onNavigate, onLogout }) {
  const [period, setPeriod] = useState("week");
  const [records, setRecords] = useState([]);

  useEffect(() => {
    loadHistory().then(setRecords);
  }, []);

  const days = getDays(period);
  const filtered = records.filter(r => r.date >= days[0]);

  // KPIs
  const journeyCount = filtered.length;
  const co2SavedGrams = filtered.reduce((sum, r) => sum + Math.max(0, r.carCo2Grams - r.co2Grams), 0);
  const co2SavedKg = (co2SavedGrams / 1000).toFixed(1);
  const avgDuration = filtered.length
    ? Math.round(filtered.reduce((sum, r) => sum + r.durationSeconds, 0) / filtered.length / 60)
    : 0;

  const modeCounts = {};
  filtered.forEach(r => {
    const mode = simplifyMode(r.modeSummary);
    modeCounts[mode] = (modeCounts[mode] || 0) + 1;
  });
  const topMode = Object.entries(modeCounts).sort((a, b) => b[1] - a[1])[0]?.[0] || "--";

  // Journeys per day
  const journeysPerDay = Object.fromEntries(days.map(d => [d, 0]));
  filtered.forEach(r => { if (r.date in journeysPerDay) journeysPerDay[r.date]++; });
  const maxJourneys = Math.max(1, ...Object.values(journeysPerDay));

  // CO2 saved per day
  const co2PerDay = Object.fromEntries(days.map(d => [d, 0]));
  filtered.forEach(r => {
    if (r.date in co2PerDay) co2PerDay[r.date] += Math.max(0, r.carCo2Grams - r.co2Grams);
  });
  const maxCo2 = Math.max(1, ...Object.values(co2PerDay));

  // Mode breakdown sorted by count
  const modeEntries = Object.entries(modeCounts).sort((a, b) => b[1] - a[1]);
  const maxModeCount = modeEntries[0]?.[1] || 1;

  // Recent journeys (backend returns newest first)
  const recent = records.slice(0, 10);

  return (
    <div className="history-page">
      <TopBar title="History" />

      <div className="period-toggle">
        <button className={`period-btn ${period === "week" ? "active" : ""}`} onClick={() => setPeriod("week")}>
          This Week
        </button>
        <button className={`period-btn ${period === "month" ? "active" : ""}`} onClick={() => setPeriod("month")}>
          This Month
        </button>
      </div>

      <div className="history-kpis">
        <div className="history-kpi">
          <div className="kpi-label">Journeys</div>
          <div className="kpi-val blue">{journeyCount}</div>
          <div className="kpi-unit">trips taken</div>
        </div>
        <div className="history-kpi">
          <div className="kpi-label">CO₂ Saved</div>
          <div className="kpi-val green">{co2SavedKg} kg</div>
          <div className="kpi-unit">vs driving</div>
        </div>
        <div className="history-kpi">
          <div className="kpi-label">Avg. Duration</div>
          <div className="kpi-val">{avgDuration}</div>
          <div className="kpi-unit">min per journey</div>
        </div>
        <div className="history-kpi">
          <div className="kpi-label">Top Mode</div>
          <div className="kpi-val" style={{ fontSize: "1.1rem" }}>{topMode}</div>
          <div className="kpi-unit">most used</div>
        </div>
      </div>

      <div className="history-section">
        <div className="section-title">Journeys per Day</div>
        {journeyCount === 0 ? (
          <div className="history-placeholder">No journeys recorded yet</div>
        ) : (
          <div className={`bar-chart ${period === "month" ? "month" : ""}`}>
            {days.map(d => (
              <div key={d} className="bar-col" data-tooltip={`${journeysPerDay[d]} trip${journeysPerDay[d] !== 1 ? "s" : ""}`}>
                <div className="bar-track">
                  <div className="bar-fill" style={{ height: `${(journeysPerDay[d] / maxJourneys) * 100}%` }} />
                </div>
                <div className="bar-label">{shortLabel(d, period)}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="history-section">
        <div className="section-title">CO₂ Saved vs Driving</div>
        {journeyCount === 0 ? (
          <div className="history-placeholder">No data yet</div>
        ) : (
          <div className={`bar-chart ${period === "month" ? "month" : ""}`}>
            {days.map(d => (
              <div key={d} className="bar-col" data-tooltip={`${(co2PerDay[d] / 1000).toFixed(2)} kg`}>
                <div className="bar-track">
                  <div className="bar-fill green" style={{ height: `${(co2PerDay[d] / maxCo2) * 100}%` }} />
                </div>
                <div className="bar-label">{shortLabel(d, period)}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="history-section">
        <div className="section-title">Transport Mode Breakdown</div>
        {modeEntries.length === 0 ? (
          <div className="history-placeholder">No data yet</div>
        ) : (
          <div className="mode-rows">
            {modeEntries.map(([mode, count]) => {
              const pct = Math.round((count / journeyCount) * 100);
              return (
                <div key={mode} className="mode-row">
                  <div className="mode-row-label">{mode}</div>
                  <div className="mode-bar-track">
                    <div className="mode-bar-fill" style={{ width: `${pct}%` }} />
                  </div>
                  <div className="mode-row-count">{pct}%</div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="history-section">
        <div className="section-title">Recent Journeys</div>
        {recent.length === 0 ? (
          <div className="history-placeholder">No journeys recorded yet</div>
        ) : (
          <div className="journey-list">
            {recent.map(r => (
              <div key={r.id} className="journey-item">
                <div className="journey-item-left">
                  <div className="journey-item-mode">{r.modeSummary}</div>
                  <div className="journey-item-via">to {r.destination}</div>
                  <div className="journey-item-date">{r.date}</div>
                </div>
                <div className="journey-item-right">
                  <div className="journey-item-duration">{Math.round(r.durationSeconds / 60)} min</div>
                  <div className="journey-item-co2">-{((r.carCo2Grams - r.co2Grams) / 1000).toFixed(2)} kg CO₂</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="logout-container">
        <button className="logout-btn" onClick={() => { logout(); onLogout(); }}>
          Log out
        </button>
      </div>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default HistoryPage;
