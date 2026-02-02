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
- A basic health check endpoint

Development is being carried out incrementally using an Agile vertical slicing approach.

## Running the Project

Backend (from the backend directory):
mvn spring-boot:run

The backend runs on port 8080.

Frontend (from the frontend directory):
npm install  
npm run dev  

The frontend runs on the default Vite development port.

## Methodology

The system is developed iteratively using Agile principles, with each sprint delivering a small vertical slice of functionality spanning the frontend and backend. Further features and data integration will be added in later stages of the project.
