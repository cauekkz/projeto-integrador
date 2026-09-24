package br.com.vanroute.backend.models.student;

import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "student_stops")
public class StudentStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private StudentStopLocationType locationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private StudentStopAction action;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "stop_id", nullable = false)
    private RouteStop stop;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StudentStopLocationType getLocationType() {
        return locationType;
    }

    public void setLocationType(StudentStopLocationType locationType) {
        this.locationType = locationType;
    }

    public StudentStopAction getAction() {
        return action;
    }

    public void setAction(StudentStopAction action) {
        this.action = action;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public RouteStop getStop() {
        return stop;
    }

    public void setStop(RouteStop stop) {
        this.stop = stop;
    }
}