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
- Relational database setup using PostgreSQL
- Database schema management using Flyway
- A basic health check endpoint that verifies backend and database availability

Development is being carried out incrementally using an Agile vertical slicing approach.

## Running the Project

### Database
From the project root:
docker compose up -d

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

The system is developed iteratively using Agile principles, with functionality delivered in small vertical slices that span the frontend, backend, and database layers. Early slices focus on establishing structure and architecture, with later slices introducing persistence, logic, and data integration.
