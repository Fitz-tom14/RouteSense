import BottomNav from "../components/BottomNav";

function RoutesPage({ activePage, onNavigate }) {
  return (
    <div className="page">
      <h2>Routes</h2>
      <p>Journey search coming soon...</p>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default RoutesPage;
