package br.com.vanroute.backend.dtos.geocode;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GeoRouteResponse(
        List<Feature> features
) {

    public record Feature(
            Properties properties,
            Geometry geometry
    ) {}

    // segments: um trecho entre cada par de paradas (tempo/distancia de cada um);
    // way_points: em que posicao da lista de coordenadas fica cada parada
    public record Properties(
            Summary summary,
            List<Segment> segments,
            @JsonProperty("way_points") List<Integer> wayPoints
    ) {}

    public record Segment(
            double distance,
            double duration
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