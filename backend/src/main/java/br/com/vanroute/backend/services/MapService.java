package br.com.vanroute.backend.services;

import java.util.Map;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.vanroute.backend.models.address.Address;

import br.com.vanroute.backend.dtos.geocode.CepResponse;
import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.vanroute.backend.exceptions.BadRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import br.com.vanroute.backend.dtos.geocode.GeocodeResponse;
import br.com.vanroute.backend.dtos.geocode.GeoRouteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import java.time.Duration;

@Service
public class MapService {

    private final WebClient webClient;

    @Value("${openrouteservice.api-key}")
    private String apiKey;

    @Value("${openrouteservice.geocode-url:https://api.openrouteservice.org/geocode/search}")
    private String geocodeUrl;

    @Value("${openrouteservice.url:https://api.openrouteservice.org/v2/directions/driving-car}")
    private String directionsUrl;

    // sem limite de tempo uma conexao velha (o servidor ja fechou) deixava o "iniciar rota" preso ~2 min
    // dentro da transacao: agora desiste rapido e a trip segue sem trajeto ate o proximo calculo
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    // o CEP e so a 1a tentativa: se demorar, desiste logo e vai pra OpenRouteService
    private static final Duration CEP_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(MapService.class);

    @Value("${cep.geocode-url:https://cep.awesomeapi.com.br/json}")
    private String cepGeocodeUrl;

    public MapService(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create(ConnectionProvider.builder("openrouteservice")
                        .maxIdleTime(Duration.ofSeconds(20))
                        .maxLifeTime(Duration.ofMinutes(5))
                        .evictInBackground(Duration.ofSeconds(30))
                        .build())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(TIMEOUT);
        this.webClient = webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    // resultado que so acha a cidade/regiao nao serve como ponto da van
    private static final Set<String> PRECISE_LAYERS = Set.of("address", "street", "venue");

    // 1o pelo CEP (AwesomeAPI): a OpenRouteService conhece pouco os enderecos do Brasil e nao achava nem ruas
    // conhecidas. Se o CEP nao servir (fora do ar, nao achou, CEP unico da cidade ou de outra cidade), busca pelo texto
    public Coordenadas searchCoord(Address address) {
        Coordenadas byCep = searchByCep(address);
        return byCep != null ? byCep : searchCoord(addressText(address));
    }

    // null = sem resultado confiavel pelo CEP (quem chamou tenta a OpenRouteService). cep.geocode-url vazio desliga
    // (os testes de API usam so o simulador)
    private Coordenadas searchByCep(Address address) {
        String zip = address.getZipCode() == null ? "" : address.getZipCode().replaceAll("\\D", "");
        if (cepGeocodeUrl == null || cepGeocodeUrl.isBlank() || zip.length() != 8) {
            return null;
        }
        try {
            CepResponse response = webClient.get()
                    .uri(cepGeocodeUrl + "/" + zip)
                    .retrieve()
                    .bodyToMono(CepResponse.class)
                    .block(CEP_TIMEOUT);
            Coordenadas coords = fromCep(response, address.getCity());
            if (coords == null) {
                log.info("CEP sem coordenada confiavel na AwesomeAPI, buscando pela OpenRouteService");
            }
            return coords;
        } catch (Exception e) {
            log.warn("Busca por CEP indisponivel ({}), buscando pela OpenRouteService", e.getMessage());
            return null;
        }
    }

    // so aceita CEP de rua (no CEP unico da cidade o address vem vazio e o ponto e o centro da cidade) e da mesma
    // cidade que a pessoa digitou (CEP errado nao pode jogar o ponto em outra cidade)
    static Coordenadas fromCep(CepResponse response, String city) {
        if (response == null || isBlank(response.address()) || isBlank(response.lat()) || isBlank(response.lng())) {
            return null;
        }
        if (!isBlank(city) && !AddressService.normalize(city).equals(AddressService.normalize(response.city()))) {
            return null;
        }
        try {
            return new Coordenadas(Double.parseDouble(response.lat()), Double.parseDouble(response.lng()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    // monta o texto sem campo vazio (antes um bairro nulo virava "null" no texto e caia em outro pais)

    static String addressText(Address address) {
        return Stream.of(address.getStreet(), address.getNumber(), address.getNeighborhood(),
                        address.getCity(), address.getState(), address.getZipCode())
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(", "));
    }

    public Coordenadas searchCoord(String address) {

        GeocodeResponse response;
        try {
            response = webClient.get()
                .uri(UriComponentsBuilder.fromUriString(geocodeUrl)
                        .queryParam("api_key", apiKey)
                        .queryParam("text", address)
                        .queryParam("boundary.country", "BR")
                        .build()
                        .toUri())
                .retrieve()
                .bodyToMono(GeocodeResponse.class)
                .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            // 403 "Quota exceeded" / 429: limite da chave do OpenRouteService (1000 buscas por dia no plano gratis)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Busca de endereços indisponível agora (limite da API de mapas). Tente mais tarde.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Busca de endereços indisponível agora. Tente mais tarde.");
        }

        if (response == null || response.features() == null || response.features().isEmpty()) {
            throw new BadRequestException("Endereço não encontrado: " + address);
        }
        GeocodeResponse.Properties properties = response.features().get(0).properties();
        if (properties != null && properties.layer() != null && !PRECISE_LAYERS.contains(properties.layer())) {
            throw new BadRequestException("Endereço não encontrado com precisão: " + address);
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

    // trajeto passando por todos os pontos na ordem (maximo 50 no plano gratis).
    // cada ponto e [longitude, latitude]; devolve GeoJSON com a linha, distancia (m) e duracao (s)
    public GeoRouteResponse directions(List<List<Double>> lngLatPoints) {
        return webClient.post()
                .uri(directionsUrl + "/geojson")
                .header("Authorization", apiKey)
                .bodyValue(Map.of("coordinates", lngLatPoints))
                .retrieve()
                .bodyToMono(GeoRouteResponse.class)
                .block(TIMEOUT);
    }
}
