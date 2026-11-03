package br.com.vanroute.backend.dtos.route;

// filtros opcionais de GET /api/route/get-all-routes-and-stops: name (parte do nome) e active
// (true = so as rotas em que o motorista esta hoje; padrao)
public record RouteFilterRequestDTO(
        String name,
        Boolean active
) {
}
