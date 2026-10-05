package by.whatsappka.media;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaVariantRepository extends JpaRepository<MediaVariant, UUID> {
}
