package com.routesense.domain.model;

// A walking link between two nearby stops (within 300 m).
// Enables multi-modal journeys where the user walks between a train station
// and a nearby bus stop to complete a transfer (e.g. Galway Ceannt → Eyre Square bus stops).
public record FootpathEdge(String toStopId, int walkSeconds) {}
