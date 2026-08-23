package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.repositories.AddressRepository;
import org.springframework.stereotype.Service;

@Service
public class AddressService {
    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    public Address addAddress(AddressRequestDTO addressRequestDTO){

        Address address = new Address();

        address.setStreet(addressRequestDTO.street());
        address.setZipCode(addressRequestDTO.zipCode());
        address.setCity(addressRequestDTO.city());
        address.setNeighborhood(addressRequestDTO.neighborhood());
        address.setNumber(addressRequestDTO.number());
        address.setState(addressRequestDTO.state());

        return addressRepository.save(address);
    }
}
