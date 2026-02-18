/**
 * Bottom navigation bar.
 * Uses activePage to highlight the current page and onNavigate to switch pages.
 */
function BottomNav({ activePage, onNavigate }) {
  const navItems = ["Home", "Map", "Routes", "Settings"];

  return (
    <nav className="bottom-nav">
      {navItems.map((name) => (
        <button
          key={name}
          className={`nav-item ${activePage === name ? "active" : ""}`}
          onClick={() => onNavigate(name)}
        >
          {name}
        </button>
      ))}
    </nav>
  );
}

export default BottomNav;
