-- Вложение сообщения чата: тип связи, который ставит только отправка сообщения, а не клиент.
ALTER TABLE media_links DROP CONSTRAINT media_links_type_allowed;
ALTER TABLE media_links ADD CONSTRAINT media_links_type_allowed
    CHECK (link_type IN ('PROFILE_AVATAR', 'PROFILE_COVER', 'GROUP_AVATAR', 'CHAT_ATTACHMENT'));
