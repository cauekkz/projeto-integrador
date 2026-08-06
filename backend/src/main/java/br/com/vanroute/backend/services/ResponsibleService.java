package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.ResponsibleRequestDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import org.springframework.stereotype.Service;

@Service
public class ResponsibleService {

    private final ResponsibleRepository responsibleRepository;
    private final UserService userService;

    public ResponsibleService(ResponsibleRepository responsibleRepository, UserService userService) {
        this.responsibleRepository = responsibleRepository;
        this.userService = userService;
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
        Responsible responsible = new Responsible();
        responsible.setUser(user);
        responsible.setFinancialStatus(FinancialStatus.PENDING);
        Responsible newResponsible = responsibleRepository.save(responsible);
        return new ResponsibleResponseDTO(
                newResponsible.getUser().getName(),
                newResponsible.getUser().getEmail(),
                newResponsible.getUser().getCpf(),
                newResponsible.getUser().getPhone(),
                newResponsible.getFinancialStatus(),
                newResponsible.getUser().getRoles()
                );
    }

}
