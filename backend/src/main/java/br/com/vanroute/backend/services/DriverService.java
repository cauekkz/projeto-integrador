package br.com.vanroute.backend.services;

import org.springframework.stereotype.Service;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.dtos.user.PixKeyDTO;
import br.com.vanroute.backend.models.user.enums.PixKeyType;
import br.com.vanroute.backend.utils.PixKeys;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    public Driver createDriver(DriverRequestDTO driverRequestDto, String cpf, String name) {

        UserCreateDTO userDto = new UserCreateDTO();
        userDto.setName(name);
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

        throw new IllegalStateException("Não foi possível gerar um código de conversa único depois de 5 tentativas");
    }

    // codigo de conversa do motorista autenticado (gerado no cadastro), pelo id do jwt
    public String getLinkCode(UUID userId) {
        return driverRepository.findById(userId)
                .map(Driver::getLinkCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
    }

    // chave Pix pra receber as parcelas; sem chave cadastrada devolve type e key nulos
    public PixKeyDTO getPixKey(UUID userId) {
        Driver driver = driver(userId);
        return new PixKeyDTO(driver.getPixKeyType(), driver.getPixKey());
    }

    @Transactional
    public PixKeyDTO setPixKey(UUID userId, PixKeyDTO request) {
        Driver driver = driver(userId);
        PixKeyType type = request == null ? null : request.type();
        driver.setPix(type, PixKeys.normalize(type, request == null ? null : request.key()));
        driverRepository.save(driver);
        return new PixKeyDTO(driver.getPixKeyType(), driver.getPixKey());
    }

    @Transactional
    public void removePixKey(UUID userId) {
        Driver driver = driver(userId);
        driver.setPix(null, null);
        driverRepository.save(driver);
    }

    private Driver driver(UUID userId) {
        return driverRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado."));
    }

}
