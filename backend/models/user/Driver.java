@Entity
@Table(name = "drivers")
@NoArgsConstructor
@Getter
@Setter

public class Driver {

    @Id
    @Column(name = "user_id")
    private Long userId;     

    @Column(name = "cnh_number", nullable = false, unique = true)
    private String cnhNumber;

    @Column(name = "cnh_expiration")
    private LocalDate cnhExpiration;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private DriverApprovalStatus approvalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private DriverType type;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

}