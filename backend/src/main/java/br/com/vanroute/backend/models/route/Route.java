package br.com.vanroute.backend.models.route;

import br.com.vanroute.backend.models.route.enums.RouteShift;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column()
    private RouteShift shift;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RouteShift getShift() {
        return shift;
    }

    public void setShift(RouteShift shift) {
        this.shift = shift;
    }

}
