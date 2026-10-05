package by.whatsappka.media;

/** Варианты изображения по стороне в пикселях. Значение хранится в БД как имя константы. */
public enum VariantKind {
    SIZE_320(320),
    SIZE_1280(1280);

    private final int maxSide;

    VariantKind(int maxSide) {
        this.maxSide = maxSide;
    }

    public int maxSide() {
        return maxSide;
    }
}
