-- Existing installations from before refresh_sessions.token_hash cannot safely recover a hash from
-- an opaque token without retaining the raw secret. Revoke those sessions instead. This migration
-- is deliberately idempotent for installations that never had the legacy table.
DO $$
BEGIN
  IF to_regclass('public.refresh_tokens') IS NOT NULL THEN
    EXECUTE 'DELETE FROM refresh_tokens';
  END IF;
END $$;

-- Before the hashing authorization-service adapter is deployed, Spring Authorization Server
-- persisted refresh-token values in oauth2_authorization. There is no safe in-place conversion
-- because old rows use the raw value for lookup, so revoke those grants rather than leave live
-- raw bearer secrets in the database. New values are SHA-256 verifiers written by that adapter.
DELETE FROM oauth2_authorization WHERE refresh_token_value IS NOT NULL;
