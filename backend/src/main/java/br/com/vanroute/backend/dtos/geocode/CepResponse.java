package br.com.vanroute.backend.dtos.geocode;

// resposta da AwesomeAPI CEP (cep.awesomeapi.com.br/json/{cep}). address vem vazio no CEP unico de cidade
// pequena (ai a coordenada e o centro da cidade); lat/lng vem como texto
public record CepResponse(
        String address,
        String city,
        String lat,
        String lng
) {}
