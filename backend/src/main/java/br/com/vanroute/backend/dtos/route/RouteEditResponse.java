package br.com.vanroute.backend.dtos.route;

import java.util.List;

// resposta de tudo que altera a rota: os pontos ja com a mudanca e o que ainda falta configurar.
// pending vazio = rota pronta (com pendencia a viagem nao inicia)
public record RouteEditResponse(
        List<RouteStopResponse> stops,
        List<RoutePendingResponse> pending
) {
}
