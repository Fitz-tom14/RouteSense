// history.js - saves and retrieves journey history from localStorage.
// Each entry captures the key metrics needed for the History dashboard.

const STORAGE_KEY = "routesense_history";

// Save a completed journey to history.
// route = the selected public transport option; carCo2Grams = car baseline CO₂.
export function saveJourney(route, carCo2Grams) {
  const history = loadHistory();

  const entry = {
    timestamp: Date.now(),
    date: new Date().toISOString().split("T")[0], // "YYYY-MM-DD"
    durationSeconds: route.durationSeconds || route.totalDurationSeconds || 0,
    co2Grams: route.co2Grams || 0,
    carCo2Grams: carCo2Grams || 0,
    modeSummary: route.modeSummary || "Transit",
    transfers: route.legs ? route.legs.filter(l => l.mode !== "WALK").length - 1 : 0,
  };

  history.push(entry);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
}

// Load all journey history entries from localStorage.
export function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
  } catch {
    return [];
  }
}
