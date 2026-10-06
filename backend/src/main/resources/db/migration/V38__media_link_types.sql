-- Все типы связей медиа из MediaLinkType: изображение публикации и обложка/аватар сообщества
-- раньше не входили в ограничение, и сохранение поста с картинкой падало на вставке связи.
ALTER TABLE media_links DROP CONSTRAINT media_links_type_allowed;
ALTER TABLE media_links ADD CONSTRAINT media_links_type_allowed
    CHECK (link_type IN (
        'PROFILE_AVATAR', 'PROFILE_COVER', 'GROUP_AVATAR', 'CHAT_ATTACHMENT',
        'COMMUNITY_AVATAR', 'COMMUNITY_COVER', 'POST_ATTACHMENT'));
