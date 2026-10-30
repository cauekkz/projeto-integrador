package br.com.vanroute.backend.models.student;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import br.com.vanroute.backend.models.address.Address;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "student_addresses")
public class StudentAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    // apelido que o responsavel ve e o motorista escolhe: "Casa", "Casa da avo", "Ingles de terca"
    @Column(nullable = false, length = 60)
    private String label;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }


    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
