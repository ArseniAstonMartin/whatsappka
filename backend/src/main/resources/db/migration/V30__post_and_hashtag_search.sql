-- Поиск постов и хештегов (FR-12). Проекцией служит сам индекс по выражению: PostgreSQL пересчитывает его
-- при правке и удалении, поэтому отдельная копия текста не нужна и не может отстать.
-- Триграммы работают на любом Unicode-тексте, включая русский, без словарей и стемминга.

CREATE INDEX posts_body_search_idx ON posts USING gin (whatsappka_search_norm(body) gin_trgm_ops)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

CREATE INDEX hashtags_name_search_idx ON hashtags USING gin (whatsappka_search_norm(normalized_name) gin_trgm_ops);
