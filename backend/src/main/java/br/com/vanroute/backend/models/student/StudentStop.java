package br.com.vanroute.backend.models.student;

import br.com.vanroute.backend.models.route.RouteStop;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "student_stops")
public class StudentStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column()
    private String type;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
