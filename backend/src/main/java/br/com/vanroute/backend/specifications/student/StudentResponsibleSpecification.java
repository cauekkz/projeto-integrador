package br.com.vanroute.backend.specifications.student;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import br.com.vanroute.backend.dtos.student.AllStudentsFilterRequestDTO;
import br.com.vanroute.backend.models.student.StudentResponsible;
public class StudentResponsibleSpecification {

    public static Specification<StudentResponsible> withFilters(
            String cpf,
            AllStudentsFilterRequestDTO filter
    ) {

        return (root, query, cb) -> {
            Predicate predicate = cb.equal(
            root.get("responsible")
                .get("user")
                .get("cpf"),
            cpf
        );
        if (filter.isAdmin()!= null){
            predicate = cb.and(predicate, cb.equal(root.get("isAdmin"), filter.isAdmin()));
        }
        if(filter.relationType() != null){
            predicate = cb.and(predicate, cb.equal(root.get("relationType"), filter.relationType()));
        }
        if(filter.studentName() != null){
            predicate = cb.and(predicate, cb.like(cb.lower( root.get("student").get("name")), filter.studentName().toLowerCase() + "%"));

        }


        return predicate;
        };
    }
}