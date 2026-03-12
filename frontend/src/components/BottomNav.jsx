// src/components/BottomNav.jsx
// A simple bottom navigation component for the RouteSense app

import React from "react";
import "./BottomNav.css"; // Import styles for the bottom navigation
function BottomNav({ activePage, onNavigate }) {
  const navItems = ["Home", "Map", "Routes", "History"];

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
