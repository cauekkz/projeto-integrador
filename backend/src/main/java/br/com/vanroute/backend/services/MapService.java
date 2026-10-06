package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.geocode.GeocodeResponse;
import br.com.vanroute.backend.dtos.geocode.GeoRouteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MapService {

    private final WebClient webClient;

    @Value("${openrouteservice.api-key}")
    private String apiKey;

    public MapService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Coordenadas searchCoord(String address) {

        GeocodeResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.heigit.org")
                        .path("/pelias/v1/search")
                        .queryParam("api_key", apiKey)
                        .queryParam("text", address)
                        .build())
                .retrieve()
                .bodyToMono(GeocodeResponse.class)
                .block();

        var coordinates = response.features()
                .get(0)
                .geometry()
                .coordinates();

        return new Coordenadas(
                coordinates.get(1), // latitude
                coordinates.get(0)  // longitude
        );
    }

    public GeoRouteResponse calcularRota(
            double longitudeOrigem,
            double latitudeOrigem,
            double longitudeDestino,
            double latitudeDestino
    ) {

        String start = longitudeOrigem + "," + latitudeOrigem;
        String end = longitudeDestino + "," + latitudeDestino;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.openrouteservice.org")
                        .path("/v2/directions/driving-car")
                        .queryParam("api_key", apiKey)
                        .queryParam("start", start)
                        .queryParam("end", end)
                        .build())
                .retrieve()
                .bodyToMono(GeoRouteResponse.class)
                .block();
    }
}