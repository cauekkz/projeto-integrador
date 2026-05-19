@Entity
@Table(name = "user_emails")
@NoArgsConstructor
@Getter
@Setter

public class UserEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "email_id", nullable = false)
    private Email email;
}