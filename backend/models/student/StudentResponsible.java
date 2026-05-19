@Entity
@Table(name = "student_responsibles")
@NoArgsConstructor
@Getter
@Setter

public class StudentResponsible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type")
    private RelationType relationType;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "responsible_id", nullable = false)
    private Responsible responsible;
}