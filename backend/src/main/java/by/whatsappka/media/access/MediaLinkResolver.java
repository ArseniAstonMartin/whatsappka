package by.whatsappka.media.access;

import java.util.UUID;

/**
 * Владелец объекта регистрирует резолвер для своего типа связи. Он решает, может ли зритель видеть файл,
 * привязанный к объекту. Сама проверка прав объекта остаётся за владельцем типа, а не за хранилищем.
 */
public interface MediaLinkResolver {

    MediaLinkType type();

    boolean canView(UUID viewerId, UUID linkId);
}
