CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    email_normalized VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    status VARCHAR(32) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by UUID REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

CREATE TABLE identity_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    action_type VARCHAR(40) NOT NULL,
    target_email VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    requested_ip INET,
    user_agent VARCHAR(512),
    CONSTRAINT chk_identity_action_type CHECK (action_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    CONSTRAINT chk_identity_action_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_identity_action_tokens_user_active
    ON identity_action_tokens(user_id, expires_at)
    WHERE consumed_at IS NULL;

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    token_family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_session_id UUID REFERENCES refresh_sessions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    device_name VARCHAR(255),
    ip_address INET,
    user_agent VARCHAR(512),
    CONSTRAINT chk_refresh_session_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_sessions_user_active
    ON refresh_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_sessions_family_id ON refresh_sessions(token_family_id);

CREATE TABLE auth_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    CONSTRAINT chk_auth_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_auth_outbox_events_pending
    ON auth_outbox_events(created_at)
    WHERE published_at IS NULL;

CREATE TABLE auth_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    subject_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(128) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    ip_address INET,
    user_agent VARCHAR(512),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_auth_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_auth_audit_events_subject_created
    ON auth_audit_events(subject_user_id, created_at DESC);
CREATE INDEX idx_auth_audit_events_type_created
    ON auth_audit_events(event_type, created_at DESC);

INSERT INTO roles (id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'CUSTOMER', 'Customer account'),
    ('00000000-0000-0000-0000-000000000002', 'SELLER', 'Seller account'),
    ('00000000-0000-0000-0000-000000000003', 'ADMIN', 'Platform administrator');

INSERT INTO permissions (id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000101', 'USER:READ', 'Read users'),
    ('00000000-0000-0000-0000-000000000102', 'USER:STATUS_WRITE', 'Change user status'),
    ('00000000-0000-0000-0000-000000000103', 'USER:ROLE_WRITE', 'Change user roles'),
    ('00000000-0000-0000-0000-000000000104', 'USER:SESSION_REVOKE', 'Revoke user sessions');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id FROM permissions;

-- Spring Authorization Server JDBC schema. Client secrets are supplied as hashes by configuration.
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) PRIMARY KEY, client_id VARCHAR(100) NOT NULL UNIQUE,
    client_id_issued_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL, client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMPTZ, client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL, redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000), scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL, token_settings VARCHAR(2000) NOT NULL
);

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY, registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL, authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000), attributes TEXT, state VARCHAR(500),
    authorization_code_value TEXT, authorization_code_issued_at TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ, authorization_code_metadata TEXT,
    access_token_value TEXT, access_token_issued_at TIMESTAMPTZ, access_token_expires_at TIMESTAMPTZ,
    access_token_metadata TEXT, access_token_type VARCHAR(100), access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT, oidc_id_token_issued_at TIMESTAMPTZ, oidc_id_token_expires_at TIMESTAMPTZ,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT, refresh_token_issued_at TIMESTAMPTZ, refresh_token_expires_at TIMESTAMPTZ,
    refresh_token_metadata TEXT, user_code_value TEXT, user_code_issued_at TIMESTAMPTZ,
    user_code_expires_at TIMESTAMPTZ, user_code_metadata TEXT, device_code_value TEXT,
    device_code_issued_at TIMESTAMPTZ, device_code_expires_at TIMESTAMPTZ, device_code_metadata TEXT
);
CREATE INDEX idx_oauth2_authorization_registered_client_id ON oauth2_authorization(registered_client_id);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL, principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
