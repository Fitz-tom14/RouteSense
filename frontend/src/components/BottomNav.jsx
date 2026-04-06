// src/components/BottomNav.jsx
// A simple bottom navigation component for the RouteSense app

import React from "react"; //import React library for building the component
import "./BottomNav.css"; // Import styles for the bottom navigation

function BottomNav({ activePage, onNavigate }) {
  const navItems = ["Home", "Map", "Routes", "History"];

  // Render the bottom navigation bar with buttons for each page. The active page is highlighted.
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
