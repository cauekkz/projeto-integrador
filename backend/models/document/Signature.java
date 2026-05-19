@Entity
@Table(name = "signatures")
@NoArgsConstructor
@Getter
@Setter

public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column()
    private SignatureStatus status;

    @Column(name = "signed_at")
    private LocalDate signedAt;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}