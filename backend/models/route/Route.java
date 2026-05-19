@Entity
@Table(name = "routes")
@NoArgsConstructor
@Getter
@Setter

public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column()
    private RouteShift shift;
}