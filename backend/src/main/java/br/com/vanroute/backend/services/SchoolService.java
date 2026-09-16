package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.school.SchoolRequestDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.repositories.SchoolRepository;
import org.springframework.stereotype.Service;

@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final AddressService addressService;
    private final MapService mapService;

    public SchoolService(SchoolRepository schoolRepository,
                         AddressService addressService, MapService mapService) {
        this.schoolRepository = schoolRepository;
        this.addressService = addressService;
        this.mapService = mapService;
    }

    public School createSchool(SchoolRequestDTO dto){
        if(schoolRepository.findByName(dto.name()).isPresent() &&
                schoolRepository.findByAddress_Street(dto.addressRequestDTO().street()).isPresent()){
            throw new RuntimeException("Already exists this school");
        }
        School school = new School();
        school.setName(dto.name());
        school.setPhone(dto.phone());
        school.setEmail(dto.email());
        schoolRepository.save(school);
        Address address = addressService.addAddress(dto.addressRequestDTO());
        String addressString = address.getStreet()
                + ", " + address.getNumber()
                + ", " + address.getNeighborhood()
                + ", " + address.getCity()
                + ", " + address.getState()
                + ", " + address.getZipCode();
        Coordenadas cords = mapService.searchCoord(addressString);
        address.setLatitude(cords.latitude());
        address.setLongitude(cords.longitude());
        return school;
    }
}
