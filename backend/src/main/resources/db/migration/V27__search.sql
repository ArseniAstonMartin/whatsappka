-- Поиск людей и сообществ (FR-12). Текст приводится к одной форме: NFKC, нижний регистр,
-- і→и, ў→у, ё→е, чтобы белорусские и русские буквы находили друг друга. Та же функция вызывается в запросах.

CREATE OR REPLACE FUNCTION whatsappka_search_norm(input text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT translate(lower(normalize(btrim(input), NFKC)), 'іўё', 'иуе')
$$;

CREATE INDEX users_username_search_idx ON users USING gin (whatsappka_search_norm(username) gin_trgm_ops);
CREATE INDEX user_profiles_display_search_idx ON user_profiles USING gin (whatsappka_search_norm(display_name) gin_trgm_ops);
CREATE INDEX groups_name_search_idx ON groups USING gin (whatsappka_search_norm(name) gin_trgm_ops);
CREATE INDEX groups_slug_search_idx ON groups USING gin (whatsappka_search_norm(slug) gin_trgm_ops);
-- Описание ищется только у публичных групп: у приватной оно не раскрывается даже через поиск.
CREATE INDEX groups_description_search_idx ON groups USING gin (whatsappka_search_norm(description) gin_trgm_ops)
    WHERE visibility = 'PUBLIC' AND hidden_at IS NULL AND deleted_at IS NULL;
