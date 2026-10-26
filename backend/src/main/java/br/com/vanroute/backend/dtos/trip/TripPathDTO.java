package br.com.vanroute.backend.dtos.trip;

import java.util.List;

// trajeto calculado pela API de rotas: linha [longitude, latitude] pro mapa, distancia (m) e duracao (s).
// null quando a API nao respondeu (a trip continua funcionando)
public record TripPathDTO(
        Double distance,
        Double duration,
        List<List<Double>> coordinates
) {
}
