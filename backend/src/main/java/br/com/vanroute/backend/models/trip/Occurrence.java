package br.com.vanroute.backend.models.trip;

import br.com.vanroute.backend.models.trip.enums.OccurrenceStatus;
import br.com.vanroute.backend.models.trip.enums.OccurrenceType;
import br.com.vanroute.backend.models.user.Driver;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "occurrences")
public class Occurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column()
    private OccurrenceType type;

    @Enumerated(EnumType.STRING)
    @Column()
    private OccurrenceStatus status;

    @Column(name = "date_time")
    private LocalDateTime dateTime;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OccurrenceType getType() {
        return type;
    }

    public void setType(OccurrenceType type) {
        this.type = type;
    }

    public OccurrenceStatus getStatus() {
        return status;
    }

    public void setStatus(OccurrenceStatus status) {
        this.status = status;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

}
