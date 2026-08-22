package br.com.vanroute.backend.models.school;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "driver_schools")
public class DriverSchools {
    @Id UUID id;
    UUID driverId;
    UUID schoolId;
    LocalDateTime createdAt;
}