package br.com.vanroute.backend.models.vehicle;

import br.com.vanroute.backend.models.vehicle.enums.VehicleStatus;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 20, nullable = false, unique = true)
    private String plate;

    @Column(length = 100)
    private String model;

    private Integer year;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column()
    private VehicleStatus status;

    @Column(length = 11, unique = true)
    private String renavam;

    @Column(length = 17, unique = true)
    private String chassis;

    @Column(name = "manufacture_year")
    private Integer manufactureYear;

    @Column(length = 50)
    private String color;

    @Column(length = 50)
    private String fuel;

    // PARTICULAR, ALUGUEL...
    @Column(length = 50)
    private String category;

    // exercicio do CRLV (ano do licenciamento)
    @Column(name = "licensing_year")
    private Integer licensingYear;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public String getRenavam() {
        return renavam;
    }

    public void setRenavam(String renavam) {
        this.renavam = renavam;
    }

    public String getChassis() {
        return chassis;
    }

    public void setChassis(String chassis) {
        this.chassis = chassis;
    }

    public Integer getManufactureYear() {
        return manufactureYear;
    }

    public void setManufactureYear(Integer manufactureYear) {
        this.manufactureYear = manufactureYear;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getFuel() {
        return fuel;
    }

    public void setFuel(String fuel) {
        this.fuel = fuel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getLicensingYear() {
        return licensingYear;
    }

    public void setLicensingYear(Integer licensingYear) {
        this.licensingYear = licensingYear;
    }
}
