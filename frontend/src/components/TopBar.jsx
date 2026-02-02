import { useState } from 'react';

function TopBar() {
  const [location, setLocation] = useState('');

  return (
    <div className="top-bar">
      <h1 className="page-title">Home</h1>
      <div className="location-select-container">
        <label htmlFor="location-select">Location</label>
        <select 
          id="location-select"
          value={location} 
          onChange={(e) => setLocation(e.target.value)}
          className="location-select"
        >
          <option value="">Select location</option>
          <option value="galway">Galway</option>
          <option value="dublin">Dublin</option>
          <option value="cork">Cork</option>
        </select>
      </div>
    </div>
  );
}

export default TopBar;
