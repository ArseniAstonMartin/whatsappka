-- Дни активности пользователя (UTC). Одна строка на пользователя и день; сам день — единица учёта для DAU.
CREATE TABLE user_activity_days (
    user_id uuid NOT NULL REFERENCES users (id),
    day date NOT NULL,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX user_activity_days_day_idx ON user_activity_days (day);
