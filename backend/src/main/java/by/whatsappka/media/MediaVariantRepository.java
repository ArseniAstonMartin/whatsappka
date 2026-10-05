package by.whatsappka.media;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaVariantRepository extends JpaRepository<MediaVariant, UUID> {

    List<MediaVariant> findByMediaId(UUID mediaId);
}
