@Entity
@Table(name = "students")
@NoArgsConstructor
@Getter
@Setter

public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @ManyToOne
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;
}