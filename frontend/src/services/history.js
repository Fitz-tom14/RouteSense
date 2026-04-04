// history.js - saves and retrieves journey history from the backend database.
// Each entry captures the key metrics needed for the History dashboard.

const API_BASE = "http://localhost:8080";

// Get the logged-in user's email to scope history per user.
function getUserId() {
  try {
    const auth = localStorage.getItem("routesense_auth");
    return auth ? JSON.parse(auth).email : "anonymous";
  } catch {
    return "anonymous";
  }
}

// Save a completed journey to the backend database.
export async function saveJourney(route, carCo2Grams, destination) {
  const entry = {
    timestamp: Date.now(),
    date: new Date().toISOString().split("T")[0], // "YYYY-MM-DD"
    durationSeconds: route.durationSeconds || route.totalDurationSeconds || 0,
    co2Grams: route.co2Grams || 0,
    carCo2Grams: carCo2Grams || 0,
    modeSummary: route.modeSummary || "Transit",
    destination: destination || "",
    transfers: route.transfers ?? 0,
    userId: getUserId(),
  };

  await fetch(`${API_BASE}/api/history`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(entry),
  });
}

// Load all journey history for the current user from the backend.
export async function loadHistory() {
  const userId = getUserId();
  try {
    const res = await fetch(`${API_BASE}/api/history?userId=${encodeURIComponent(userId)}`);
    return res.ok ? await res.json() : [];
  } catch {
    return [];
  }
}
