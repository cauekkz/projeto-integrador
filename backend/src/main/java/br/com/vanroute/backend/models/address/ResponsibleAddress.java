package br.com.vanroute.backend.models.address;

import br.com.vanroute.backend.models.user.Responsible;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "responsible_addresses")
public class ResponsibleAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "responsible_id", nullable = false)
    private Responsible responsible;

    @ManyToOne
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Responsible getResponsible() {
        return responsible;
    }

    public void setResponsible(Responsible responsible) {
        this.responsible = responsible;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

}
