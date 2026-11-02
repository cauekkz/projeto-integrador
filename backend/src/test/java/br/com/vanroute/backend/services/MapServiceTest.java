package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.address.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
