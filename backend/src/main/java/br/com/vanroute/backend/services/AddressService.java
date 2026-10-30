package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.repositories.AddressRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

// o unico lugar que cria endereco. Endereco e compartilhado (responsavel, aluno, escola e ponto da rota apontam pra ele),
// por isso nunca e editado: mudou o endereco = liga num endereco novo.
@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final MapService mapService;

    public AddressService(AddressRepository addressRepository, MapService mapService) {
        this.addressRepository = addressRepository;
        this.mapService = mapService;
    }

    // se o mesmo endereco ja existe (mesmo CEP + numero), so devolve ele: nao duplica nem chama a API de mapas.
    // senao cria e busca a coordenada uma vez. A coordenada que vier do app e ignorada (nao da pra mandar uma falsa)
    public Address findOrCreate(AddressRequestDTO dto) {
        String key = key(dto);
        return addressRepository.findFirstByAddressKeyAndLatitudeIsNotNullOrderByIdAsc(key).orElseGet(() -> {
            Address address = new Address();
            address.setStreet(dto.street());
            address.setZipCode(dto.zipCode());
            address.setCity(dto.city());
            address.setNeighborhood(dto.neighborhood());
            address.setNumber(dto.number());
            address.setState(dto.state());
            address.setAddressKey(key);
            Coordenadas cords = mapService.searchCoord(address);
            address.setLatitude(cords.latitude());
            address.setLongitude(cords.longitude());
            return addressRepository.save(address);
        });
    }

    // mesmo endereco = mesmo CEP + mesmo numero (o jeito de escrever a rua nao importa).
    // CEP terminado em 000 pode ser o CEP unico de uma cidade pequena: ai compara tambem a rua e a cidade,
    // pra nao juntar duas casas de ruas diferentes. Mesma regra da migration V26 (que calcula pros antigos)
    static String key(AddressRequestDTO dto) {
        String zip = dto.zipCode() == null ? "" : dto.zipCode().replaceAll("\\D", "");
        String number = dto.number() == null ? "" : dto.number().toString();
        if (zip.length() == 8 && !zip.endsWith("000") && !number.isEmpty()) {
            return zip + "|" + number;
        }
        return String.join("|", zip, number, normalize(dto.street()), normalize(dto.city()));
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
