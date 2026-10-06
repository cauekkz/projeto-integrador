package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.school.SchoolRequestDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.school.DriverSchools;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.SchoolRepository;
import org.springframework.stereotype.Service;

@Service
public class DriverSchoolService {

    private final SchoolRepository schoolRepository;
    private final AddressService addressService;
    private final MapService mapService;
    private final DriverRepository driverRepository;
    private final DriverSchoolsRepository driverSchoolsRepository;

    public DriverSchoolService(SchoolRepository schoolRepository,
                               AddressService addressService, MapService mapService, DriverRepository driverRepository, DriverSchoolsRepository driverSchoolsRepository) {
        this.schoolRepository = schoolRepository;
        this.addressService = addressService;
        this.mapService = mapService;
        this.driverRepository = driverRepository;
        this.driverSchoolsRepository = driverSchoolsRepository;
    }

    public School createSchool(SchoolRequestDTO dto, String cpf){
        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        if (schoolRepository.findByName(dto.name()).isPresent()
                && schoolRepository.findByAddress_Street(dto.addressRequestDTO().street()).isPresent()) {
            throw new RuntimeException("Already exists this school");
        }

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


        School school = new School();
        school.setName(dto.name());
        school.setPhone(dto.phone());
        school.setEmail(dto.email());
        school.setAddress(address);

        schoolRepository.save(school);
        DriverSchools driverSchools = new DriverSchools();
        driverSchools.setSchool(school);
        driverSchools.setDriver(driver);

        driverSchoolsRepository.save(driverSchools);

        return school;
    }
}
