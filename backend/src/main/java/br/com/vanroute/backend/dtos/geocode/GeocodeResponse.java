package br.com.vanroute.backend.dtos.geocode;

import java.util.List;

public record GeocodeResponse(
        List<Feature> features
) {
    public record Feature(
            Geometry geometry,
            Properties properties
    ) {}

    // layer: address, street, venue... ou so a cidade (locality), regiao, pais
    public record Properties(
            String layer
    ) {}

    public record Geometry(
            List<Double> coordinates
    ) {}
}