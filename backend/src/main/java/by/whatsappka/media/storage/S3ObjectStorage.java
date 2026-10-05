package by.whatsappka.media.storage;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Адаптер S3 поверх MinIO. Работает с bucket, который создаёт инициализация compose, и никогда его не публикует. */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final String bucket;

    public S3ObjectStorage(S3Client s3, MediaProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(size).build(),
                    RequestBody.fromInputStream(content, size)
            );
        } catch (SdkException e) {
            throw new StorageException("Не удалось сохранить объект", e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new ObjectMissingException();
            }
            throw new StorageException("Не удалось прочитать объект", e);
        } catch (SdkException e) {
            throw new StorageException("Не удалось прочитать объект", e);
        }
    }

    @Override
    public Optional<ObjectInfo> head(String key) {
        try {
            HeadObjectResponse response = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new ObjectInfo(response.contentLength(), response.contentType()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageException("Не удалось проверить объект", e);
        } catch (SdkException e) {
            throw new StorageException("Не удалось проверить объект", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new StorageException("Не удалось удалить объект", e);
        }
    }
}
