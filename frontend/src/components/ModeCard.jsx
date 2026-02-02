function ModeCard() {
  const modes = [
    { name: 'Car', percentage: '--%' },
    { name: 'Bus', percentage: '--%' },
    { name: 'Bike', percentage: '--%' }
  ];

  return (
    <div className="card mode-card">
      <h3 className="card-title">Transport Mode Used</h3>
      <div className="mode-items">
        {modes.map((mode) => (
          <div key={mode.name} className="mode-item">
            <div className="mode-name">{mode.name}</div>
            <div className="mode-percentage">{mode.percentage}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default ModeCard;
