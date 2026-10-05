-- Причина, по которой worker вернул отложенную запись в DRAFT (TASK-043). Видна автору в черновиках.
ALTER TABLE posts ADD COLUMN schedule_failure_reason varchar(40);
