package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.dtos.user.ResponsibleRequestDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.address.ResponsibleAddress;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.repositories.ResponsibleAddressRepository;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import org.springframework.stereotype.Service;

@Service
public class ResponsibleService {

    private final ResponsibleRepository responsibleRepository;
    private final UserService userService;
    private final AddressService addressService;
    private final MapService mapService;
    private final ResponsibleAddressRepository responsibleAddressRepository;

    public ResponsibleService(ResponsibleRepository responsibleRepository, UserService userService, AddressService addressService, MapService mapService, ResponsibleAddressRepository responsibleAddressRepository) {
        this.responsibleRepository = responsibleRepository;
        this.userService = userService;
        this.addressService = addressService;
        this.mapService = mapService;
        this.responsibleAddressRepository = responsibleAddressRepository;
    }

    public ResponsibleResponseDTO createResponsible(ResponsibleRequestDTO responsibleRequestDTO){
        UserCreateDTO userDto = new UserCreateDTO();
        userDto.setName(responsibleRequestDTO.getName());
        userDto.setCpf(responsibleRequestDTO.getCpf());
        userDto.setPasswordHash(responsibleRequestDTO.getPassword());
        userDto.setEmail(responsibleRequestDTO.getEmail());
        userDto.setPhone(responsibleRequestDTO.getPhone());
        userDto.setRole(RoleTypeEnum.ROLE_RESPONSIBLE);

        User user = userService.createUser(userDto);

        Address address = addressService.addAddress(responsibleRequestDTO.getAddressRequestDTO());
        Responsible responsible = new Responsible();
        responsible.setUser(user);
        responsible.setFinancialStatus(FinancialStatus.PENDING);
        responsible.setAddress(address);
        String addressString = address.getStreet()
                + ", " + address.getNumber()
                + ", " + address.getNeighborhood()
                + ", " + address.getCity()
                + ", " + address.getState()
                + ", " + address.getZipCode();
       Coordenadas cords = mapService.searchCoord(addressString);
        address.setLatitude(cords.latitude());
        address.setLongitude(cords.longitude());
        ResponsibleAddress responsibleAddress = new ResponsibleAddress();
        responsibleAddress.setResponsible(responsible);
        responsibleAddress.setAddress(address);
        responsibleAddressRepository.save(responsibleAddress);

        Responsible newResponsible = responsibleRepository.save(responsible);
        return new ResponsibleResponseDTO(
                newResponsible.getUser().getName(),
                newResponsible.getUser().getEmail(),
                newResponsible.getUser().getCpf(),
                newResponsible.getUser().getPhone(),
                newResponsible.getFinancialStatus(),
                newResponsible.getUser().getRoles(),
                new AddressResponseDTO(
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
    }

}
