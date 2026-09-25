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
import jakarta.transaction.Transactional;
import br.com.vanroute.backend.utils.CodeGenerator;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserService userService;
    private final CodeGenerator codeGenerator;
    public DriverService(DriverRepository driverRepository, UserService userService, CodeGenerator codeGenerator) {
        this.driverRepository = driverRepository;
        this.userService = userService;
        this.codeGenerator = codeGenerator;
    }

    @Transactional
    public Driver createDriver(DriverRequestDTO driverRequestDto, String cpf) {

        UserCreateDTO userDto = new UserCreateDTO();
        userDto.setName(driverRequestDto.getName());
        userDto.setCpf(cpf);
        userDto.setPasswordHash(driverRequestDto.getPassword());
        userDto.setEmail(driverRequestDto.getEmail());
        userDto.setPhone(driverRequestDto.getPhone());
        userDto.setRole(RoleTypeEnum.ROLE_DRIVER);

        User user = userService.createUser(userDto);

        Driver driver = new Driver();
        driver.setUser(user);
        driver.setApprovalStatus(DriverApprovalStatus.APPROVED);

        for (int attempt = 0; attempt < 5; attempt++) {

            String linkCode = codeGenerator.generateCode();
            driver.setLinkCode(linkCode);

            try {
                return driverRepository.saveAndFlush(driver);

            } catch (DataIntegrityViolationException e) {
            }
        }

        throw new RuntimeException("Não foi possível gerar um código único.");
    }

}
