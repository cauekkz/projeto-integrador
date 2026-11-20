package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.address.Address;
import org.junit.jupiter.api.Test;

import br.com.vanroute.backend.dtos.geocode.CepResponse;
import br.com.vanroute.backend.dtos.geocode.Coordenadas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MapServiceTest {

    @Test
    void addressTextSkipsEmptyFields() {
        Address address = new Address();
        address.setStreet("Rua Escola");
        address.setNumber(10);
        address.setNeighborhood(null);
        address.setCity("Sao Paulo");
        address.setState(" ");
        address.setZipCode("01000-000");

        // bairro nulo nao pode virar "null" no texto (a busca real caia em Portugal)
        assertEquals("Rua Escola, 10, Sao Paulo, 01000-000", MapService.addressText(address));
    }

    @Test
    void cepOfStreetGivesItsCoordinates() {
        CepResponse cep = new CepResponse("Rua Augusta", "São Paulo", "-23.5569581", "-46.6589677");

        // cidade digitada sem acento e com outra caixa continua sendo a mesma cidade
        assertEquals(new Coordenadas(-23.5569581, -46.6589677), MapService.fromCep(cep, "sao paulo"));
    }

    @Test
    void cityWideCepIsNotPrecise() {
        // CEP unico de cidade pequena: sem rua, a coordenada e o centro da cidade
        CepResponse cep = new CepResponse("", "Guaxupé", "-21.3054884", "-46.7084932");

        assertNull(MapService.fromCep(cep, "Guaxupé"));
    }

    @Test
    void cepOfAnotherCityIsIgnored() {
        CepResponse cep = new CepResponse("Rua Augusta", "São Paulo", "-23.5569581", "-46.6589677");

        assertNull(MapService.fromCep(cep, "Campinas"));
    }

    @Test
    void cepWithoutCoordinatesIsIgnored() {
        assertNull(MapService.fromCep(new CepResponse("Rua Augusta", "São Paulo", "", null), "São Paulo"));
        assertNull(MapService.fromCep(new CepResponse("Rua Augusta", "São Paulo", "abc", "-46.6"), "São Paulo"));
        assertNull(MapService.fromCep(null, "São Paulo"));
    }
}
