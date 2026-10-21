package br.com.vanroute.backend.repositories;

import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StudentStopRepository extends JpaRepository<StudentStop, UUID> {
    boolean existsByStudentIdAndStopId(UUID studentId, UUID stopId);
    List<StudentStop> findByStudentIdAndStopRouteIdAndAction(UUID studentId, UUID routeId, StudentStopAction action);
    List<StudentStop> findByStopId(UUID stopId);

    @Query("SELECT ss FROM StudentStop ss JOIN FETCH ss.student WHERE ss.stop.route.id = :routeId")
    List<StudentStop> findByRouteWithStudent(@Param("routeId") UUID routeId);

    @Query("SELECT DISTINCT ss.stop.route FROM StudentStop ss WHERE ss.student.id = :studentId")
    List<Route> findRoutesOfStudent(@Param("studentId") UUID studentId);
    boolean existsByStopId(UUID stopId);

    // algum aluno dessa rota tem contrato ativo com essa escola
    @Query("SELECT CASE WHEN COUNT(ss) > 0 THEN true ELSE false END FROM StudentStop ss, UserDriverContract u " +
            "WHERE ss.stop.route.id = :routeId AND u.student = ss.student AND u.school.id = :schoolId " +
            "AND u.contract.status = br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE")
    boolean existsStudentUsingSchoolInRoute(@Param("routeId") UUID routeId, @Param("schoolId") UUID schoolId);
}
