@Entity
@Table(name = "vehicles")
@NoArgsConstructor
@Getter
@Setter

public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false, unique = true)
    private String plate;

    @Column(length = 100)
    private String model;

    private Integer year;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column()
    private VehicleStatus status;
}