import { useState } from "react";

// Pages
import HomePage from "./pages/HomePage";
import MapPage from "./pages/MapPage";
import LoginPage from "./pages/LoginPage";
import RoutesPage from "./pages/RoutesPage";
import HistoryPage from "./pages/HistoryPage";
import { saveJourney } from "./services/history";

// Auth helper
import { getStoredAuth } from "./services/auth";

function App() {
  // Check if user already has a stored session
  const [isAuthed, setIsAuthed] = useState(() =>
    Boolean(getStoredAuth())
  );

  // Decide which page to show first
  const [activePage, setActivePage] = useState(
    isAuthed ? "Home" : "Login"
  );

  // Selected journey from Routes page — drives Home page KPIs
  const [selectedJourney, setSelectedJourney] = useState(null);
  const [selectedJourneyCarCo2, setSelectedJourneyCarCo2] = useState(0);

  function handleSelectJourney(route, carBaselineCo2Grams, destination) {
    setSelectedJourney(route);
    setSelectedJourneyCarCo2(carBaselineCo2Grams);
    saveJourney(route, carBaselineCo2Grams, destination);
    setActivePage("Home");
  }

  // After login → mark user as authenticated
  function handleLoginSuccess() {
    setIsAuthed(true);
    setActivePage("Home");
  }

  // After logout → return to login screen
  function handleLogout() {
    setIsAuthed(false);
    setActivePage("Login");
  }

  // Block app if not logged in
  if (!isAuthed) {
    return <LoginPage onLoginSuccess={handleLoginSuccess} />;
  }

  // Simple page switching (no router yet)
  if (activePage === "Home") {
    return (
      <HomePage
        activePage={activePage}
        onNavigate={setActivePage}
        selectedJourney={selectedJourney}
        carBaselineCo2Grams={selectedJourneyCarCo2}
      />
    );
  }

  if (activePage === "Map") {
    return <MapPage activePage={activePage} onNavigate={setActivePage} />;
  }

  if (activePage === "Routes") {
    return (
      <RoutesPage
        activePage={activePage}
        onNavigate={setActivePage}
        onSelectJourney={handleSelectJourney}
      />
    );
  }

  if (activePage === "History") {
    return (
      <HistoryPage
        activePage={activePage}
        onNavigate={setActivePage}
        onLogout={handleLogout}
      />
    );
  }

  return null;
}

export default App;
