import TopBar from '../components/TopBar';
import KpiCard from '../components/KpiCard';
import ModeCard from '../components/ModeCard';
import SummaryCard from '../components/SummaryCard';
import BottomNav from '../components/BottomNav';
import '../styles/home.css';

function HomePage() {
  return (
    <div className="home-page">
      <TopBar />
      
      <main className="main-content">
        <div className="cards-grid">
          <KpiCard 
            title="KPI: Journey Time"
            subtitle="Avg. Journey Time"
            value="--"
            unit="min"
          />
          
          <KpiCard 
            title="KPI: CO₂ Emissions"
            subtitle="Est. CO₂ per Journey"
            value="--"
            unit="kg CO₂"
          />
          
          <ModeCard />
          
          <SummaryCard />
        </div>
        
        <div className="action-button-container">
          <button className="view-map-button">View Map</button>
        </div>
      </main>
      
      <BottomNav />
    </div>
  );
}

export default HomePage;
