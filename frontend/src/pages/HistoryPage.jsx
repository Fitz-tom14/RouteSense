// HistoryPage - shows a dashboard of the user's journey history stored in localStorage.
// Users can toggle between a weekly and monthly view.
// Displays: summary KPIs, journeys-per-day bar chart, CO₂ saved bar chart,
// transport mode breakdown, and a list of recent journeys.

import { useEffect, useState } from "react";
import TopBar from "../components/TopBar";
import BottomNav from "../components/BottomNav";
import { loadHistory } from "../services/history";
import { logout } from "../services/auth";
import "../styles/history.css";

// Helpers

// Build an array of { label, date, count, co2Saved } for the last `days` days.
function buildDailyData(history, days) {
  const result = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = d.toISOString().split("T")[0];
    const dayEntries = history.filter(j => j.date === dateStr);

    // Short label: "Mon", "Tue" etc. for week; "1", "2" etc. for month
    const label = days === 7
      ? (i === 0 ? "Today" : d.toLocaleDateString("en-IE", { weekday: "short" }))
      : String(d.getDate());

    result.push({
      label,
      date: dateStr,
      count: dayEntries.length,
      co2Saved: dayEntries.reduce((sum, j) => sum + Math.max(0, j.carCo2Grams - j.co2Grams), 0),
    });
  }
  return result;
}

// Format CO₂ grams into a readable string (g or kg).
function formatCo2(grams) {
  if (grams >= 1000) return `${(grams / 1000).toFixed(1)} kg`;
  return `${Math.round(grams)} g`;
}

// Extract the primary transport mode from a modeSummary string like "Walk → Bus 405" → Bus
const MODE_RULES = [
  { match: /train/i,  label: "Train",  icon: "🚆" },
  { match: /tram/i,   label: "Tram",   icon: "🚊" },
  { match: /bus/i,    label: "Bus",    icon: "🚌" },
  { match: /bike/i,   label: "Bike",   icon: "🚲" },
  { match: /walk/i,   label: "Walk",   icon: "🚶" },
];

function simplifyMode(modeSummary) {
  const str = modeSummary || "";
  for (const rule of MODE_RULES) {
    if (rule.match.test(str)) return { label: rule.label, icon: rule.icon };
  }
  return { label: "Transit", icon: "🚍" };
}

//  Bar Chart Component

// Renders a simple CSS bar chart. `data` is an array of { label, value }.
// `color` is "blue" (default) or "green".
function BarChart({ data, color = "blue", period, tooltipFn }) {
  const maxVal = Math.max(...data.map(d => d.value), 1);
  return (
    <div className={`bar-chart ${period === "month" ? "month" : ""}`}>
      {data.map(d => (
        <div
          className="bar-col"
          key={d.label + d.date}
          data-tooltip={tooltipFn ? tooltipFn(d) : undefined}
        >
          <div className="bar-track">
            <div
              className={`bar-fill ${color === "green" ? "green" : ""}`}
              style={{ height: `${(d.value / maxVal) * 100}%` }}
            />
          </div>
          <span className="bar-label">{d.label}</span>
        </div>
      ))}
    </div>
  );
}

// Main Page

function HistoryPage({ activePage, onNavigate, onLogout }) {
  const [period, setPeriod] = useState("week"); // "week" | "month"
  const [history, setHistory] = useState([]);

  // Load journey history from localStorage on mount.
  useEffect(() => {
    setHistory(loadHistory());
  }, []);

  // Filter to the selected period.
  const days = period === "week" ? 7 : 30;
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  const filtered = history.filter(j => j.timestamp >= cutoff);

  //KPI calculations
  const totalJourneys = filtered.length;

  const totalCo2Saved = filtered.reduce(
    (sum, j) => sum + Math.max(0, j.carCo2Grams - j.co2Grams), 0
  );

  const avgDurationMins = totalJourneys
    ? Math.round(filtered.reduce((sum, j) => sum + j.durationSeconds, 0) / totalJourneys / 60)
    : 0;

  // Count how many times each simplified mode was used.
  const modeCounts = filtered.reduce((acc, j) => {
    const { label } = simplifyMode(j.modeSummary);
    acc[label] = (acc[label] || 0) + 1;
    return acc;
  }, {});
  const topMode = Object.entries(modeCounts).sort((a, b) => b[1] - a[1])[0]?.[0] || "--";

  // Chart data
  const dailyData = buildDailyData(filtered, days);
  const journeyChartData = dailyData.map(d => ({ ...d, value: d.count }));
  const co2ChartData    = dailyData.map(d => ({ ...d, value: Math.round(d.co2Saved) }));

  // Mode breakdown sorted by most used.
  const modeEntries = Object.entries(modeCounts).sort((a, b) => b[1] - a[1]);

  // Recent journeys — last 5, newest first.
  const recent = [...history]
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, 5);

  // Render

  // Empty state: no journeys recorded yet.
  if (history.length === 0) {
    return (
      <div className="history-page">
        <TopBar />
        <div className="history-empty">
          <strong>No journeys yet</strong>
          <p>Search a route and tap <strong>Select this route</strong> to start building your history.</p>
        </div>
        <div className="logout-container">
          <button className="logout-btn" onClick={() => { logout(); onLogout(); }}>Log out</button>
        </div>
        <BottomNav activePage={activePage} onNavigate={onNavigate} />
      </div>
    );
  }

  return (
    <div className="history-page">
      <TopBar />

      {/* Week / Month toggle */}
      <div className="period-toggle">
        <button
          className={`period-btn ${period === "week" ? "active" : ""}`}
          onClick={() => setPeriod("week")}
        >
          This Week
        </button>
        <button
          className={`period-btn ${period === "month" ? "active" : ""}`}
          onClick={() => setPeriod("month")}
        >
          This Month
        </button>
      </div>

      {/* Summary KPIs */}
      <div className="history-kpis">
        <div className="history-kpi">
          <div className="kpi-label">Journeys</div>
          <div className="kpi-val blue">{totalJourneys}</div>
          <div className="kpi-unit">trips taken</div>
        </div>

        <div className="history-kpi">
          <div className="kpi-label">CO₂ Saved</div>
          <div className="kpi-val green">{formatCo2(totalCo2Saved)}</div>
          <div className="kpi-unit">vs driving</div>
        </div>

        <div className="history-kpi">
          <div className="kpi-label">Avg. Duration</div>
          <div className="kpi-val">{avgDurationMins}</div>
          <div className="kpi-unit">min per journey</div>
        </div>

        <div className="history-kpi">
          <div className="kpi-label">Top Mode</div>
          <div className="kpi-val" style={{ fontSize: "1.1rem" }}>{topMode}</div>
          <div className="kpi-unit">most used</div>
        </div>
      </div>

      {/* Journeys per day bar chart */}
      <div className="history-section">
        <div className="section-title">Journeys per Day</div>
        <BarChart
          data={journeyChartData}
          color="blue"
          period={period}
          tooltipFn={d => d.value === 1 ? "1 trip" : `${d.value} trips`}
        />
      </div>

      {/* CO₂ saved per day bar chart */}
      <div className="history-section">
        <div className="section-title">CO₂ Saved vs Driving (g)</div>
        <BarChart
          data={co2ChartData}
          color="green"
          period={period}
          tooltipFn={d => `${formatCo2(d.value)} CO₂ saved`}
        />
      </div>

      {/* Mode breakdown */}
      {modeEntries.length > 0 && (
        <div className="history-section">
          <div className="section-title">Transport Mode Breakdown</div>
          <div className="mode-rows">
            {modeEntries.map(([mode, count]) => {
              const { icon } = MODE_RULES.find(r => r.label === mode) || { icon: "🚍" };
              const pct = Math.round((count / totalJourneys) * 100);
              return (
              <div className="mode-row" key={mode}>
                <span className="mode-row-label">{icon} {mode}</span>
                <div className="mode-bar-track">
                  <div
                    className="mode-bar-fill"
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <span className="mode-row-count">{pct}%</span>
              </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Recent journeys list */}
      <div className="history-section">
        <div className="section-title">Recent Journeys</div>
        <div className="journey-list">
          {recent.map(j => (
            <div className="journey-item" key={j.timestamp}>
              <div className="journey-item-left">
                <span className="journey-item-mode">
                  {j.destination ? `Trip to ${j.destination}` : (j.modeSummary || "Transit")}
                </span>
                <span className="journey-item-date">
                  {new Date(j.timestamp).toLocaleDateString("en-IE", {
                    weekday: "short", day: "numeric", month: "short"
                  })}
                </span>
              </div>
              <div className="journey-item-right">
                <span className="journey-item-duration">
                  {Math.round(j.durationSeconds / 60)} min
                </span>
                <span className="journey-item-co2">
                  -{formatCo2(Math.max(0, j.carCo2Grams - j.co2Grams))} CO₂
                </span>
              </div>
            </div>
          ))}
        </div>
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
