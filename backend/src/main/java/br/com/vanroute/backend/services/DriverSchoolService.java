package br.com.vanroute.backend.services;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.transaction.annotation.Transactional;

import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.dtos.school.SchoolRequestDTO;
import br.com.vanroute.backend.dtos.school.SchoolResponseDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.school.DriverSchools;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.SchoolRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

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

    @Transactional
    public School createSchool(SchoolRequestDTO dto, String cpf){
        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));

        // escola ja cadastrada (mesmo nome e rua): so vincula o motorista a ela
        Optional<School> existing = schoolRepository
                .findByNameAndAddress_Street(dto.name(), dto.addressRequestDTO().street());
        if (existing.isPresent()) {
            School school = existing.get();
            if (driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), school.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver already linked to this school");
            }
            DriverSchools driverSchools = new DriverSchools();
            driverSchools.setSchool(school);
            driverSchools.setDriver(driver);
            driverSchoolsRepository.save(driverSchools);
            return school;
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

    public List<SchoolResponseDTO> findMySchools(String cpf) {
        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));
        return driverSchoolsRepository.findByDriverWithSchool(driver.getUserId()).stream()
                .map(DriverSchools::getSchool)
                .map(school -> {
                    Address address = school.getAddress();
                    return new SchoolResponseDTO(
                            school.getId(),
                            school.getName(),
                            school.getPhone(),
                            school.getEmail(),
                            address == null ? null : new AddressResponseDTO(
                                    address.getId(),
                                    address.getStreet(),
                                    address.getZipCode(),
                                    address.getCity(),
                                    address.getNeighborhood(),
                                    address.getNumber(),
                                    address.getState(),
                                    address.getLatitude(),
                                    address.getLongitude()
                            )
                    );
                })
                .toList();
    }
}
