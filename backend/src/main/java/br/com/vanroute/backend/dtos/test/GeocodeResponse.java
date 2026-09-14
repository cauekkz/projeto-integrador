package br.com.vanroute.backend.dtos.test;

import java.util.List;

public record GeocodeResponse(
        List<Feature> features
) {
    public record Feature(
            Geometry geometry
    ) {}

    public record Geometry(
            List<Double> coordinates
    ) {}
}