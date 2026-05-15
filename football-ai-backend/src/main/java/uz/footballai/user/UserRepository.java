package uz.footballai.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedFalse(String email);

    /**
     * Club bilan birga yuklash — LazyInitializationException oldini olish uchun.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.club WHERE u.email = :email AND u.deleted = false")
    Optional<User> findByEmailWithClub(@Param("email") String email);

    /**
     * ID bo'yicha Club bilan birga yuklash.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.club WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdWithClub(@Param("id") UUID id);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndDeletedFalse(UUID id);
}
