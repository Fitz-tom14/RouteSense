import { useState } from "react";
import HomePage from "./pages/HomePage";
import MapPage from "./pages/MapPage";

/**
 * Simple page switching without adding routing complexity yet.
 * This keeps the app easy to explain at this stage.
 */
function App() {
  const [activePage, setActivePage] = useState("Home");

  return (
    <>
      {activePage === "Home" && (
        <HomePage activePage={activePage} onNavigate={setActivePage} />
      )}

      {activePage === "Map" && (
        <MapPage activePage={activePage} onNavigate={setActivePage} />
      )}
    </>
  );
}

export default App;
