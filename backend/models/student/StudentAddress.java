@Entity
@Table(name = "student_addresses")
@NoArgsConstructor
@Getter
@Setter

public class StudentAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String weekdays;

    @Column()
    private Boolean active = true;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;
}