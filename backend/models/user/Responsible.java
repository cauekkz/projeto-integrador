@Entity
@Table(name = "responsibles")
@NoArgsConstructor
@Getter
@Setter

public class Responsible {

    @Id
    @Column(name = "user_id")
    private Long userId;


    @Enumerated(EnumType.STRING)
    @Column(name = "financial_status", nullable = false)
    private FinancialStatus financialStatus;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;
}