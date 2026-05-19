@Entity
@Table(name = "responsible_addresses")
@NoArgsConstructor
@Getter
@Setter

public class ResponsibleAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column()
    private Boolean active = true;

    @ManyToOne
    @JoinColumn(name = "responsible_id", nullable = false)
    private Responsible responsible;

    @ManyToOne
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;
}