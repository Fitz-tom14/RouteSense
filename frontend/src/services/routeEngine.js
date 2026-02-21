/**
 * Dijkstra route engine (demo graph).
 *
 * Nodes = places/stops
 * Edges = connections with minutes + CO2
 *
 * We run Dijkstra 3 ways:
 *  - FASTEST  (weight = minutes)
 *  - GREENEST (weight = CO2 with a small time factor)
 *  - BALANCED (minutes + CO2 weight)
 */

const DEMO_GRAPH = {
  EyreSquare: [
    edge("NUIG", "WALK", 18, 0.0),
    edge("CeanntStation", "WALK", 8, 0.0),
    edge("Salthill", "BUS", 22, 1.8),
  ],
  NUIG: [
    edge("EyreSquare", "WALK", 18, 0.0),
    edge("Salthill", "WALK", 25, 0.0),
    edge("Oranmore", "BUS", 30, 2.2),
  ],
  CeanntStation: [
    edge("EyreSquare", "WALK", 8, 0.0),
    edge("Oranmore", "TRAIN", 12, 0.7),
  ],
  Oranmore: [
    edge("CeanntStation", "TRAIN", 12, 0.7),
    edge("NUIG", "BUS", 30, 2.2),
    edge("Salthill", "BUS", 35, 2.8),
  ],
  Salthill: [
    edge("EyreSquare", "BUS", 22, 1.8),
    edge("NUIG", "WALK", 25, 0.0),
    edge("Oranmore", "BUS", 35, 2.8),
  ],
};

function edge(to, mode, minutes, co2Kg) {
  return { to, mode, minutes, co2Kg };
}

export function buildRoutesDijkstra(start, end) {
  const startNode = normalisePlace(start);
  const endNode = normalisePlace(end);

  if (!DEMO_GRAPH[startNode] || !DEMO_GRAPH[endNode]) {
    return { routes: [], recommendedId: null };
  }

  const fastest = dijkstra(DEMO_GRAPH, startNode, endNode, "FASTEST");
  const greenest = dijkstra(DEMO_GRAPH, startNode, endNode, "GREENEST");
  const balanced = dijkstra(DEMO_GRAPH, startNode, endNode, "BALANCED");

  const routes = [
    toRoute("fastest", "Fastest", fastest),
    toRoute("greenest", "Greenest", greenest),
    toRoute("balanced", "Balanced", balanced),
  ].filter((r) => r.path.length);

  // Simple story for supervisor: we recommend the balanced option
  const recommendedId = routes.find((r) => r.id === "balanced") ? "balanced" : (routes[0]?.id ?? null);

  return { routes, recommendedId };
}

function toRoute(id, label, result) {
  return {
    id,
    label,
    path: result.path,
    legs: result.legs,
    timeMin: result.totalMin,
    co2Kg: round1(result.totalCo2),
    transfers: Math.max(0, result.legs.length - 1),
    notes: buildNote(label),
  };
}

function buildNote(label) {
  if (label === "Fastest") return "Dijkstra minimising time.";
  if (label === "Greenest") return "Dijkstra minimising CO₂.";
  return "Dijkstra balancing time and CO₂.";
}

function dijkstra(graph, start, target, mode) {
  const dist = {};
  const prev = {};
  const visited = new Set();

  for (const node of Object.keys(graph)) {
    dist[node] = Infinity;
    prev[node] = null;
  }
  dist[start] = 0;

  while (true) {
    const current = pickMinUnvisited(dist, visited);
    if (!current) break;
    if (current === target) break;

    visited.add(current);

    for (const e of graph[current]) {
      const alt = dist[current] + weight(e, mode);
      if (alt < dist[e.to]) {
        dist[e.to] = alt;
        prev[e.to] = current;
      }
    }
  }

  const path = rebuildPath(prev, start, target);
  const { totalMin, totalCo2, legs } = totalsFromPath(graph, path);

  return { path, totalMin, totalCo2, legs, score: dist[target] };
}

function weight(e, mode) {
  if (mode === "FASTEST") return e.minutes;

  if (mode === "GREENEST") {
    // CO2 is primary; a small time factor prevents silly long detours
    return e.co2Kg * 50 + e.minutes * 0.2;
  }

  // BALANCED: time + CO2 weight
  return e.minutes + e.co2Kg * 12;
}

function pickMinUnvisited(dist, visited) {
  let bestNode = null;
  let bestVal = Infinity;

  for (const node of Object.keys(dist)) {
    if (visited.has(node)) continue;
    if (dist[node] < bestVal) {
      bestVal = dist[node];
      bestNode = node;
    }
  }
  return bestNode;
}

function rebuildPath(prev, start, target) {
  const out = [];
  let cur = target;

  if (cur !== start && prev[cur] === null) return [];

  while (cur) {
    out.push(cur);
    if (cur === start) break;
    cur = prev[cur];
  }

  return out.reverse();
}

function totalsFromPath(graph, path) {
  let totalMin = 0;
  let totalCo2 = 0;
  const legs = [];

  for (let i = 0; i < path.length - 1; i++) {
    const from = path[i];
    const to = path[i + 1];
    const e = graph[from]?.find((x) => x.to === to);
    if (!e) continue;

    totalMin += e.minutes;
    totalCo2 += e.co2Kg;
    legs.push(e.mode);
  }

  return { totalMin, totalCo2, legs };
}

function normalisePlace(text) {
  const t = (text || "").trim().toLowerCase();

  if (t.includes("eyre")) return "EyreSquare";
  if (t.includes("nuig") || t.includes("university")) return "NUIG";
  if (t.includes("ceannt") || t.includes("station")) return "CeanntStation";
  if (t.includes("oran")) return "Oranmore";
  if (t.includes("salth")) return "Salthill";

  // allow exact node typed as fallback (remove spaces)
  return (text || "").replace(/\s+/g, "");
}

function round1(x) {
  return Math.round(x * 10) / 10;
}
