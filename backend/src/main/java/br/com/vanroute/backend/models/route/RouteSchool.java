package br.com.vanroute.backend.models.route;

import br.com.vanroute.backend.models.school.School;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "route_schools")
public class RouteSchool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "school_order", nullable = false)
    private Integer schoolOrder;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getSchoolOrder() {
        return schoolOrder;
    }

    public void setSchoolOrder(Integer schoolOrder) {
        this.schoolOrder = schoolOrder;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public School getSchool() {
        return school;
    }

    public void setSchool(School school) {
        this.school = school;
    }

}
