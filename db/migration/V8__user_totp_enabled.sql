-- AUTH01: TOTP flag on user (secret + recovery codes may already exist via dbCreate update)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS totp_enabled boolean NOT NULL DEFAULT false;
