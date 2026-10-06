package br.com.vanroute.backend.mapper;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentAddressResponseDTO;
import br.com.vanroute.backend.dtos.student.StudentResponseDTO;
import br.com.vanroute.backend.dtos.student.StudentResponsibleResponseDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Responsible;
import org.springframework.stereotype.Component;

@Component
public class StudentResponsibleMapper {

    public StudentResponsibleResponseDTO toResponse(
            Student student,
            br.com.vanroute.backend.models.student.StudentAddress studentAddress,
            Responsible responsible
    ) {

        return new StudentResponsibleResponseDTO(
                toStudentResponse(student, studentAddress),
                toResponsibleResponse(responsible)
        );
    }

    private StudentResponseDTO toStudentResponse(
            Student student,
            br.com.vanroute.backend.models.student.StudentAddress studentAddress
    ) {

        StudentAddressResponseDTO addressResponse = null;

        if (studentAddress != null) {
            addressResponse = toStudentAddressResponse(studentAddress);
        }

        return new StudentResponseDTO(
                student.getId(),
                student.getName(),
                student.getNotes(),
                student.getBirthDate(),
                addressResponse
        );
    }

    private StudentAddressResponseDTO toStudentAddressResponse(
            br.com.vanroute.backend.models.student.StudentAddress studentAddress
    ) {

        Address address = studentAddress.getAddress();

        return new StudentAddressResponseDTO(
                studentAddress.getId(),
                address.getStreet(),
                address.getStreet(),
                address.getZipCode(),
                address.getCity(),
                address.getNeighborhood(),
                address.getNumber(),
                address.getState(),
                address.getLatitude(),
                address.getLongitude()
        );
    }

    private ResponsibleResponseDTO toResponsibleResponse(
            Responsible responsible
    ) {

        return new ResponsibleResponseDTO(
                responsible.getUser().getName(),
                responsible.getUser().getEmail(),
                responsible.getUser().getCpf(),
                responsible.getUser().getPhone(),
                responsible.getFinancialStatus(),
                responsible.getUser().getRoles(),
                new AddressResponseDTO(
                        responsible.getAddress().getId(),
                        responsible.getAddress().getStreet(),
                        responsible.getAddress().getZipCode(),
                        responsible.getAddress().getCity(),
                        responsible.getAddress().getNeighborhood(),
                        responsible.getAddress().getNumber(),
                        responsible.getAddress().getState(),
                        responsible.getAddress().getLatitude(),
                        responsible.getAddress().getLongitude()
        )
        );
    }
}