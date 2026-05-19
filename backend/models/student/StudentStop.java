@Entity
@Table(name = "student_stops")
@NoArgsConstructor
@Getter
@Setter

public class StudentStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column()
    private String type;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "stop_id", nullable = false)
    private RouteStop stop;
}