function BottomNav() {
  const navItems = [
    { name: 'Home', active: true },
    { name: 'Map', active: false },
    { name: 'Routes', active: false },
    { name: 'Settings', active: false }
  ];

  return (
    <nav className="bottom-nav">
      {navItems.map((item) => (
        <button 
          key={item.name}
          className={`nav-item ${item.active ? 'active' : ''}`}
        >
          {item.name}
        </button>
      ))}
    </nav>
  );
}

export default BottomNav;
