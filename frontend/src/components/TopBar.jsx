import { useState } from 'react'; // Import React and useState hook for managing component state

//  A simple top bar component for the RouteSense app, displaying the page title and a location selector dropdown.
function TopBar() {
  const [location, setLocation] = useState('');

  // Render the top bar with a title and a location selector. The selected location is stored in the component state.
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
