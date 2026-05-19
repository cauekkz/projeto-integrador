@Entity
@Table(name = "contracts")
@NoArgsConstructor
@Getter
@Setter

public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_a_type")
    private String partyAType;

    @Column(name = "party_a_id")
    private Long partyAId;

    @Column(name = "party_b_type")
    private String partyBType;

    @Column(name = "party_b_id")
    private Long partyBId;

    @Column(length = 100)
    private String periodicity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column()
    private ContractStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}