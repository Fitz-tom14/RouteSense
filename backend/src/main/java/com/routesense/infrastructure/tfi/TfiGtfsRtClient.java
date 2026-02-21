package com.routesense.infrastructure.tfi;

import com.google.transit.realtime.GtfsRealtime;
import com.routesense.application.port.VehiclePositionsDataSource;
import com.routesense.domain.model.VehiclePosition;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
public class TfiGtfsRtClient implements VehiclePositionsDataSource {

    private final RestTemplate restTemplate = new RestTemplate();
    private final TfiGtfsRtProperties props;

    public TfiGtfsRtClient(TfiGtfsRtProperties props) {
        this.props = props;
    }

    @Override
    public List<VehiclePosition> getLiveVehiclePositions() {
        byte[] feedBytes = fetchFeedBytes();
        if (feedBytes == null || feedBytes.length == 0) return List.of();

        try {
            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(feedBytes);
            List<VehiclePosition> out = new ArrayList<>();

            for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
                if (!entity.hasVehicle()) continue;

                GtfsRealtime.VehiclePosition vp = entity.getVehicle();
                if (!vp.hasPosition()) continue;

                String vehicleId = vp.hasVehicle() ? vp.getVehicle().getId() : entity.getId();
                Double lat = (double) vp.getPosition().getLatitude();
                Double lon = (double) vp.getPosition().getLongitude();
                Long ts = vp.hasTimestamp() ? vp.getTimestamp() : null;

                String routeId = null;
                if (vp.hasTrip() && vp.getTrip().hasRouteId()) {
                    routeId = vp.getTrip().getRouteId();
                }

                out.add(new VehiclePosition(vehicleId, lat, lon, routeId, ts));
            }

            return out;
        } catch (Exception e) {
            // keep it simple for now; later you can log properly
            return List.of();
        }
    }

    private byte[] fetchFeedBytes() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", props.getApiKey()); // key stays on backend only
        headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                props.getVehiclePositionsUrl(),
                HttpMethod.GET,
                entity,
                byte[].class
        );

        if (!response.getStatusCode().is2xxSuccessful()) return null;
        return response.getBody();
    }
}