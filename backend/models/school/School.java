@Entity
@Table(name = "schools")
@NoArgsConstructor
@Getter
@Setter

public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private String email;

    @OneToOne
    @JoinColumn(name = "address_id", unique = true)
    private Address address;
}