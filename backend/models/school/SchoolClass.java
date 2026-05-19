@Entity
@Table(name = "classes")
@NoArgsConstructor
@Getter
@Setter

public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;
}