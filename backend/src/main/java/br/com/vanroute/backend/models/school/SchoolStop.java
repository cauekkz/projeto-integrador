package br.com.vanroute.backend.models.school;

import br.com.vanroute.backend.models.route.RouteStop;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "school_stops")
public class SchoolStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stop_id", nullable = false)
    private RouteStop stop;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public School getSchool() {
        return school;
    }

    public void setSchool(School school) {
        this.school = school;
    }

    public RouteStop getStop() {
        return stop;
    }

    public void setStop(RouteStop stop) {
        this.stop = stop;
    }
}
