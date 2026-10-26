package br.com.vanroute.backend.models.route;

import br.com.vanroute.backend.models.vehicle.Vehicle;
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

    // van padrao da rota
    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;


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

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }
}
