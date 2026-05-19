@Entity
@Table(name = "addresses")
@NoArgsConstructor
@Getter
@Setter

public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String street;

    @Column(name = "zip_code", length = 20, nullable = false)
    private String zipCode;

    @Column(length = 100, nullable = false)
    private String city;

    @Column(length = 100)
    private String neighborhood;

    private Integer number;

    @Column(length = 50)
    private String state;

    @Column(columnDefinition = "POINT")
    private String coordinates;
}