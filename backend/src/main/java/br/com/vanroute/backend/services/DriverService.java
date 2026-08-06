    package br.com.vanroute.backend.services;

    import br.com.vanroute.backend.exceptions.UserOrDriverOrResponsibleAlreadyRegisteredException;
    import org.springframework.stereotype.Service;

    import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
    import br.com.vanroute.backend.dtos.user.UserCreateDTO;
    import br.com.vanroute.backend.models.user.Driver;
    import br.com.vanroute.backend.models.user.User;
    import br.com.vanroute.backend.models.user.enums.DriverApprovalStatus;
    import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
    import br.com.vanroute.backend.repositories.DriverRepository;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserService userService;

    public DriverService(DriverRepository driverRepository, UserService userService) {
        this.driverRepository = driverRepository;
        this.userService = userService;
    }

    public Driver createDriver(DriverRequestDTO driverRequestDto, String cpf) {

        if (userService.findByCpf((cpf)).isPresent()) {
            throw new UserOrDriverOrResponsibleAlreadyRegisteredException("Usuario/Motorista já cadastrado");
        }

        UserCreateDTO userDto = new UserCreateDTO();
        userDto.setName(driverRequestDto.getName());
        userDto.setCpf(cpf);
        userDto.setPasswordHash(driverRequestDto.getPassword());
        userDto.setEmail(driverRequestDto.getEmail());
        userDto.setPhone(driverRequestDto.getPhone());
        userDto.setRole(RoleTypeEnum.ROLE_DRIVER);

        User user = this.userService.createUser(userDto);
        Driver driver = new Driver();
        driver.setUser(user);
        // aprova geral por enquanto fds
        driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
        driverRepository.save(driver);
        return driver;

    }
     
}
