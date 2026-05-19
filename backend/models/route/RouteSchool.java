@Entity
@Table(name = "route_schools")
@NoArgsConstructor
@Getter
@Setter

public class RouteSchool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_order", nullable = false)
    private Integer schoolOrder;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne
    @JoinColumn(name = "school_id", nullable = false)
    private School school;
}