package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.exceptions.BadRequestException;
import org.springframework.web.util.UriComponentsBuilder;
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

    @Value("${openrouteservice.geocode-url:https://api.heigit.org/pelias/v1/search}")
    private String geocodeUrl;

    public MapService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Coordenadas searchCoord(String address) {

        GeocodeResponse response = webClient.get()
                .uri(UriComponentsBuilder.fromUriString(geocodeUrl)
                        .queryParam("api_key", apiKey)
                        .queryParam("text", address)
                        .build()
                        .toUri())
                .retrieve()
                .bodyToMono(GeocodeResponse.class)
                .block();

        if (response == null || response.features() == null || response.features().isEmpty()) {
            throw new BadRequestException("Endereço não encontrado: " + address);
        }

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