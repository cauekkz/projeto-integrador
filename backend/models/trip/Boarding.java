@Entity
@Table(name = "boardings")
@NoArgsConstructor
@Getter
@Setter

public class Boarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
}