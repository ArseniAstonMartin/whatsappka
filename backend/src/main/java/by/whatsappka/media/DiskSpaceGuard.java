package by.whatsappka.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Не допускает новые загрузки, когда на диске мало свободного места. Ошибка измерения считается нехваткой места. */
@Component
public class DiskSpaceGuard {

    private final MediaLimits limits;

    public DiskSpaceGuard(MediaLimits limits) {
        this.limits = limits;
    }

    public boolean hasRoomForUpload() {
        try {
            long usable = Files.getFileStore(Path.of(limits.diskPath())).getUsableSpace();
            return usable >= limits.minFreeDiskBytes();
        } catch (IOException e) {
            return false;
        }
    }
}
