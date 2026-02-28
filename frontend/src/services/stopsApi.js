// Calls backend stop search endpoint and returns results
export async function searchStops(query) {

  // build URL with encoded query so special characters don’t break it
  const url = `http://localhost:8080/api/stops/search?query=${encodeURIComponent(query)}`;

  const res = await fetch(url);

  // basic error handling
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const data = await res.json();

  // make sure we always return an array
  return Array.isArray(data) ? data : [];
}