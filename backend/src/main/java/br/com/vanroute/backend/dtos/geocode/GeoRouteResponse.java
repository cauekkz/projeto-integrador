package br.com.vanroute.backend.dtos.geocode;

import java.util.List;

public record GeoRouteResponse(
        List<Feature> features
) {

    public record Feature(
            Properties properties,
            Geometry geometry
    ) {}

    public record Properties(
            Summary summary
    ) {}

    public record Summary(
            double distance,
            double duration
    ) {}

    public record Geometry(
            String type,
            List<List<Double>> coordinates
    ) {}
}