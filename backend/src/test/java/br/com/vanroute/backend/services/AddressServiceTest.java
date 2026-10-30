package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.repositories.AddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private MapService mapService;

    @InjectMocks
    private AddressService addressService;

    private static AddressRequestDTO dto(String street, String zip, String city, Double lat) {
        return new AddressRequestDTO(street, zip, city, "Centro", 100, "SP", lat, lat);
    }

    @Test
    void sameCepAndNumberIsTheSameAddressNoMatterHowTheStreetIsWritten() {
        assertEquals(AddressService.key(dto("Rua da Consolação", "01302-100", "São Paulo", null)),
                AddressService.key(dto("R. CONSOLACAO", "01302100", "sp", null)));
        // outro numero = outro endereco
        assertNotEquals(AddressService.key(dto("Rua da Consolação", "01302-100", "São Paulo", null)),
                AddressService.key(new AddressRequestDTO("Rua da Consolação", "01302-100", "São Paulo", "Centro", 101, "SP", null, null)));
    }

    @Test
    void cepEndingIn000AlsoComparesTheStreet() {
        // CEP unico de cidade pequena: mesmo CEP e numero, ruas diferentes = enderecos diferentes
        assertNotEquals(AddressService.key(dto("Rua das Flores", "13800-000", "Mogi Mirim", null)),
                AddressService.key(dto("Rua do Comercio", "13800-000", "Mogi Mirim", null)));
        assertEquals(AddressService.key(dto("Rua das Flores", "13800-000", "Mogi Mirim", null)),
                AddressService.key(dto("RUA DAS FLORES", "13800000", "mogi mirim", null)));
    }

    @Test
    void existingAddressIsReusedWithoutCallingTheMapApi() {
        Address existing = new Address();
        when(addressRepository.findFirstByAddressKeyAndLatitudeIsNotNullOrderByIdAsc(any())).thenReturn(Optional.of(existing));

        assertSame(existing, addressService.findOrCreate(dto("Rua da Consolação", "01302-000", "São Paulo", null)));
        verifyNoInteractions(mapService);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void newAddressIsGeocodedOnceAndTheAppCoordinateIsIgnored() {
        when(addressRepository.findFirstByAddressKeyAndLatitudeIsNotNullOrderByIdAsc(any())).thenReturn(Optional.empty());
        when(mapService.searchCoord(any(Address.class))).thenReturn(new Coordenadas(-23.55, -46.65));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        // o app tenta mandar uma coordenada falsa (0, 0)
        Address created = addressService.findOrCreate(dto("Rua Nova", "01000-000", "São Paulo", 0.0));

        assertEquals(-23.55, created.getLatitude());
        // CEP terminado em 000: a chave leva a rua e a cidade
        assertEquals("01000000|100|rua nova|sao paulo", created.getAddressKey());
        verify(mapService, times(1)).searchCoord(any(Address.class));
    }
}
