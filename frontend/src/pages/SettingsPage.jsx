import BottomNav from "../components/BottomNav";
import { logout } from "../services/auth";

function SettingsPage({ activePage, onNavigate, onLogout }) {
  function handleLogout() {
    logout();
    onLogout();
  }

  return (
    <div className="page">
      <h2>Settings</h2>

      <button onClick={handleLogout}>Log out</button>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default SettingsPage;
