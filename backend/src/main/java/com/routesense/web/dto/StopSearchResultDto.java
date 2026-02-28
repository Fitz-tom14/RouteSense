package com.routesense.web.dto;

// Used to send stop search results back to the frontend.
// Just the basic info needed to show a stop on the map or in a list.
public record StopSearchResultDto(

        // stop id from GTFS / database
        String id,

        // display name of the stop
        String name,

        // coordinates for map marker
        double lat,
        double lon

) {}