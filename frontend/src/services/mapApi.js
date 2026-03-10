const BASE_URL = "http://localhost:8080/api/map";

// mapApi.js -
// Service functions to interact with the backend API for fetching stops and departures based on location, transport modes, and live/static schedule preference.
export async function fetchStops({ location, modes, live }) {
  try {
    const params = new URLSearchParams();
    if (location) params.set("location", location);
    if (modes && modes.length > 0) params.set("modes", modes.join(","));
    params.set("live", String(live));

    const res = await fetch(`${BASE_URL}/stops?${params.toString()}`);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: Failed to fetch stops`);
    }
    return res.json();
  } catch (err) {
    console.error("Error fetching stops:", err);
    throw err;
  }
}

// Fetches upcoming departures for a specific stop, with an option to specify whether to use live schedule data or static data.
// The function constructs the API endpoint URL using the stop ID and live parameter, and handles errors by logging them and rethrowing.
export async function fetchDepartures(stopId, live) {
  try {
    const res = await fetch(`${BASE_URL}/stops/${encodeURIComponent(stopId)}/departures?live=${String(live)}`);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: Failed to fetch departures`);
    }
    return res.json();
  } catch (err) {
    console.error("Error fetching departures:", err);
    throw err;
  }
}
