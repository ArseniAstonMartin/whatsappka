package by.whatsappka.media.processing;

import by.whatsappka.media.MediaType;
import by.whatsappka.media.VariantKind;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;

/**
 * Декодирование, проверка предела и создание вариантов. Новые изображения собираются заново,
 * поэтому метаданные исходника (EXIF) в вариантах не переносятся. Класс не обращается к хранилищу.
 */
public final class ImageProcessor {

    /** Предел площади после декодирования (FR-10): 25 мегапикселей. */
    public static final long MAX_PIXELS = 25_000_000L;

    private static final float JPEG_QUALITY = 0.85f;

    private ImageProcessor() {
    }

    public record Variant(VariantKind kind, byte[] content, String mime, int width, int height) {
    }

    public record Result(int width, int height, List<Variant> variants) {
    }

    public static Result process(byte[] original, MediaType type) {
        BufferedImage image = decode(original, type);
        int width = image.getWidth();
        int height = image.getHeight();
        List<Variant> variants = new ArrayList<>();
        for (VariantKind kind : VariantKind.values()) {
            // Меньшую сторону исходника не увеличиваем: если он не больше предела, вариант не создаётся.
            if (Math.max(width, height) <= kind.maxSide()) {
                continue;
            }
            variants.add(scaled(image, type, kind));
        }
        return new Result(width, height, variants);
    }

    static BufferedImage decode(byte[] original, MediaType type) {
        String format = switch (type) {
            case JPEG -> "jpeg";
            case PNG -> "png";
            case WEBP -> "webp";
            default -> throw new ImageRejectedException("unsupported_format", "Формат не является изображением");
        };
        ImageReader reader = readerFor(format);
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            reader.setInput(stream);
            long width = reader.getWidth(0);
            long height = reader.getHeight(0);
            if (width <= 0 || height <= 0) {
                throw new ImageRejectedException("decode_failed", "Изображение не удалось прочитать");
            }
            if (width * height > MAX_PIXELS) {
                throw new ImageRejectedException("image_too_large", "Изображение больше 25 мегапикселей");
            }
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new ImageRejectedException("decode_failed", "Изображение не удалось прочитать");
            }
            return image;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImageRejectedException rejected) {
                throw rejected;
            }
            throw new ImageRejectedException("decode_failed", "Изображение не удалось прочитать");
        } finally {
            reader.dispose();
        }
    }

    private static ImageReader readerFor(String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new ImageRejectedException("decode_failed", "Нет декодера для формата");
        }
        return readers.next();
    }

    private static Variant scaled(BufferedImage source, MediaType type, VariantKind kind) {
        double factor = (double) kind.maxSide() / Math.max(source.getWidth(), source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        boolean jpeg = type == MediaType.JPEG;
        BufferedImage target = new BufferedImage(width, height, jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        try {
            return jpeg
                    ? new Variant(kind, writeJpeg(target), "image/jpeg", width, height)
                    : new Variant(kind, writePng(target), "image/png", width, height);
        } catch (IOException e) {
            throw new ImageRejectedException("processing_failed", "Не удалось создать превью");
        }
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("PNG writer unavailable");
        }
        return out.toByteArray();
    }

    /** Отказ по содержимому: повтор не поможет, задание завершается с причиной. */
    public static final class ImageRejectedException extends RuntimeException {

        private final String code;

        public ImageRejectedException(String code, String detail) {
            super(detail);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
