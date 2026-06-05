package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.dtos.user.IcpExtractedInfo;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserService userService;

    public DriverService(DriverRepository driverRepository, UserService userService) {
        this.driverRepository = driverRepository;
        this.userService = userService;
    }

    public Driver createDriver(IcpExtractedInfo icpInfo, DriverRequestDTO driverDto) {
    
        if (userService.findByCpf(icpInfo.getCpf()).isPresent()) {
            throw new RuntimeException("Usuario/Motorista já cadastrado");
        }

        UserCreateDTO userDto = new UserCreateDTO();
        userDto.setName(icpInfo.getName());
        userDto.setCpf(icpInfo.getCpf());
        userDto.setPasswordHash(driverDto.getPassword());
        userDto.setEmail(driverDto.getEmail());
        userDto.setRole(RoleTypeEnum.ROLE_DRIVER);

        this.userService.createUser(userDto);
        /*
            @Column(name = "cnh_number", nullable = false, unique = true)
    private String cnhNumber;

    @Column(name = "cnh_expiration")
    private LocalDate cnhExpiration;

 */                                                                                                     
        //testar se a classe pra verificar e extrair cnh funciona, dps extrair os dados da cnh criar o driverCreateDTO, dps criar o driver
        return null;
    }
}
