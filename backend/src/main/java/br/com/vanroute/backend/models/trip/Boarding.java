package br.com.vanroute.backend.models.trip;

import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "boardings")
public class Boarding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column()
    private BoardingStatus status;

    @Column(name = "boarding_time")
    private LocalDateTime boardingTime;

    @Column(name = "unboarding_time")
    private LocalDateTime unboardingTime;

    @ManyToOne
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BoardingStatus getStatus() {
        return status;
    }

    public void setStatus(BoardingStatus status) {
        this.status = status;
    }

    public LocalDateTime getBoardingTime() {
        return boardingTime;
    }

    public void setBoardingTime(LocalDateTime boardingTime) {
        this.boardingTime = boardingTime;
    }

    public LocalDateTime getUnboardingTime() {
        return unboardingTime;
    }

    public void setUnboardingTime(LocalDateTime unboardingTime) {
        this.unboardingTime = unboardingTime;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

}
