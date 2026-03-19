import TopBar from '../components/TopBar';
import KpiCard from '../components/KpiCard';
import ModeCard from '../components/ModeCard';
import SummaryCard from '../components/SummaryCard';
import BottomNav from '../components/BottomNav';
import '../styles/home.css';

// HomePage.jsx -
// React component for the home page, displaying key insights about the selected journey such as duration, CO₂ emissions, transport modes used, and a summary comparing it to a baseline car journey. If no journey is selected, prompts the user to search for a route.
function HomePage({ activePage, onNavigate, selectedJourney, carBaselineCo2Grams }) {
  const journey = selectedJourney;

  // Calculates the journey duration in minutes, and formats the CO₂ emissions for display. 
  // If no journey is selected, shows placeholders instead. The page includes a top bar, a grid of cards showing the KPIs and summary, and a bottom navigation for switching between pages. If a journey is selected and has a recommendation reason, it displays that reason in a banner at the top of the insights section.
  const durationMins = journey
    ? Math.round((journey.durationSeconds || journey.totalDurationSeconds || 0) / 60)
    : null;

    // Formats the CO₂ emissions for display, converting to kg if over 1000g and rounding appropriately.
    // If no journey is selected, shows '--' as a placeholder. The unit is displayed as 'g CO₂' or 'kg CO₂' based on the value.
  const co2Grams = journey?.co2Grams || 0;
  const co2Display = co2Grams >= 1000
    ? `${(co2Grams / 1000).toFixed(1)}`
    : `${Math.round(co2Grams)}`;
  const co2Unit = co2Grams >= 1000 ? 'kg CO₂' : 'g CO₂';

  // If no journey is selected, prompts the user to search for a route and select it to see insights.
  // If a journey is selected and has a recommendation reason, displays that reason in a banner at the top of the insights section. The main content area includes a grid of cards showing the journey time, CO₂ emissions, transport modes used, and a summary comparing it to driving. At the bottom, there's a button to search for another route, which navigates back to the Routes page.
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

          <ModeCard legs={journey?.legs || []} />

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
