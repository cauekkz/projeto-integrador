@Entity
@Table(name = "phones")
@NoArgsConstructor
@Getter
@Setter

public class Phone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}