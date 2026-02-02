function KpiCard({ title, subtitle, value, unit }) {
  return (
    <div className="card kpi-card">
      <h3 className="card-title">{title}</h3>
      <p className="card-subtitle">{subtitle}</p>
      <div className="kpi-value">
        <span className="value-number">{value}</span>
        <span className="value-unit">{unit}</span>
      </div>
    </div>
  );
}

export default KpiCard;
