@Entity
@Table(name = "ratings")
@NoArgsConstructor
@Getter
@Setter

public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 3, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column()
    private LocalDateTime date;

    @ManyToOne
    @JoinColumn(name = "responsible_id", nullable = false)
    private Responsible responsible;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;
}