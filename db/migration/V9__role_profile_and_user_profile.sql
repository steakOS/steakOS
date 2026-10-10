-- PDR-025: role profiles + user profile linkage
CREATE TABLE IF NOT EXISTS role_profile (
    id              bigserial PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    profile_id      varchar(64) NOT NULL UNIQUE,
    label           varchar(128) NOT NULL,
    default_home    varchar(128) NOT NULL,
    spring_roles_json text NOT NULL,
    matrix_json     text NOT NULL,
    matrix_version  int NOT NULL DEFAULT 1,
    system_preset   boolean NOT NULL DEFAULT true
);

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS role_profile_id varchar(64);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS permission_override_json text;
