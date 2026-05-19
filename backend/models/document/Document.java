@Entity
@Table(name = "documents")
@NoArgsConstructor
@Getter
@Setter

public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String url;

    private Integer version;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column()
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column()
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type")
    private DocumentEntityType entityType;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;
}