package it.penny.api.security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PennyUserRepository extends JpaRepository<PennyUser,Long> {
 Optional<PennyUser> findByEmailIgnoreCase(String email);
}
