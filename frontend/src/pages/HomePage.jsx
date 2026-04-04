import TopBar from '../components/TopBar';
import KpiCard from '../components/KpiCard';
import ModeCard from '../components/ModeCard';
import SummaryCard from '../components/SummaryCard';
import BottomNav from '../components/BottomNav';
import '../styles/home.css';

// Shows KPI cards for the journey the user selected on the Routes page.
// If nothing is selected yet, the cards show '--' placeholders and prompt them to search.
function HomePage({ activePage, onNavigate, selectedJourney, carBaselineCo2Grams }) {
  const journey = selectedJourney;

  // durationSeconds and totalDurationSeconds come from two different parts of the API — guard for both
  const durationMins = journey
    ? Math.round((journey.durationSeconds || journey.totalDurationSeconds || 0) / 60)
    : null;

  // ?. is optional chaining — safely reads co2Grams even if journey is null, instead of crashing
  // Show in grams below 1kg, switch to kg with one decimal above — keeps numbers readable
  const co2Grams = journey?.co2Grams || 0;
  const co2Display = co2Grams >= 1000
    ? `${(co2Grams / 1000).toFixed(1)}`
    : `${Math.round(co2Grams)}`;
  const co2Unit = co2Grams >= 1000 ? 'kg CO₂' : 'g CO₂';

  return (
    <div className="home-page">
      <TopBar title="Home" />

      <main className="main-content">
        {!journey ? (
          <div className="journey-prompt">
            Search a route and tap <strong>Select this route</strong> to see your journey insights here.
          </div>
        ) : (
          journey.recommendationReason && (
            <div className="journey-reason-banner">
              ★ {journey.recommendationReason}
            </div>
          )
        )}

        <div className="cards-grid">
          <KpiCard
            title="Journey Time"
            subtitle={journey ? (journey.modeSummary || 'Selected route') : 'Avg. Journey Time'}
            value={durationMins ?? '--'}
            unit="min"
          />

          <KpiCard
            title="CO₂ Emissions"
            subtitle={journey ? 'This journey' : 'Est. CO₂ per Journey'}
            value={journey ? co2Display : '--'}
            unit={journey ? co2Unit : 'kg CO₂'}
          />

          <ModeCard legs={journey?.legs || []} />{/* || [] means ModeCard always gets a list, never undefined */}

          <SummaryCard journey={journey} carBaselineCo2Grams={carBaselineCo2Grams || 0} />
        </div>

        <div className="action-button-container">
          <button className="view-map-button" onClick={() => onNavigate("Routes")}>
            {journey ? 'Search another route' : 'Search a route'}
          </button>
        </div>
      </main>

      <BottomNav activePage={activePage} onNavigate={onNavigate} />
    </div>
  );
}

export default HomePage;
