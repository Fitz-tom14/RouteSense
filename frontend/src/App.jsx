import { useState } from "react";

// Pages
import HomePage from "./pages/HomePage";
import MapPage from "./pages/MapPage";
import LoginPage from "./pages/LoginPage";
import RoutesPage from "./pages/RoutesPage";
import SettingsPage from "./pages/SettingsPage";

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
    return <HomePage activePage={activePage} onNavigate={setActivePage} />;
  }

  if (activePage === "Map") {
    return <MapPage activePage={activePage} onNavigate={setActivePage} />;
  }

  if (activePage === "Routes") {
    return <RoutesPage activePage={activePage} onNavigate={setActivePage} />;
  }

  if (activePage === "Settings") {
    return (
      <SettingsPage
        activePage={activePage}
        onNavigate={setActivePage}
        onLogout={handleLogout}
      />
    );
  }

  return null;
}

export default App;
