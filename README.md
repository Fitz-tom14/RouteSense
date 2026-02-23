# RouteSense

RouteSense is a web-based application developed as a Final Year Project. The project explores how journey efficiency and environmental impact can be presented through a simple, interactive web interface.

## Project Structure

The repository is organised as a basic full-stack application:

- frontend/ – React application built with Vite  
- backend/ – Spring Boot application using Java  

## Current Status

The project currently includes:
- Frontend and backend setup
- A Home page layout implemented on the frontend
- A backend endpoint providing placeholder data for the Home page
- A backend journey search endpoint (`/api/journeys/search`) used by the Routes page
- A map slice with stop/departure endpoints
- A basic health check endpoint

Development is being carried out incrementally using an Agile vertical slicing approach.

## Running the Project

### Backend
From the backend directory:
mvn spring-boot:run

The backend runs on port 8080.

### Frontend
From the frontend directory:
npm install  
npm run dev  

The frontend runs on the default Vite development port.

## Methodology

The system is developed iteratively using Agile principles, with functionality delivered in small vertical slices that span frontend and backend layers. Early slices focus on establishing structure and architecture, with later slices introducing additional features in self-contained verticals.
