package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

// o responsavel so ve em quais rotas o filho esta, sem pontos nem ordem
public record StudentRouteResponse(
        UUID id,
        String name
) {
}
