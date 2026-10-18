CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(20) NOT NULL,
    username_normalized VARCHAR(20) COLLATE utf8mb4_0900_bin NOT NULL,
    password_hash VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    bio VARCHAR(200) NOT NULL DEFAULT '',
    avatar_file_id BIGINT NULL,
    role VARCHAR(16) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT 'USER',
    status VARCHAR(16) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT 'ACTIVE',
    auth_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username_normalized (username_normalized),
    KEY idx_user_created (created_at DESC, id DESC),
    KEY idx_user_role_created (role, created_at, id),
    KEY idx_user_status_created (status, created_at DESC, id DESC),
    KEY idx_user_avatar (avatar_file_id),
    CONSTRAINT ck_user_username_length CHECK (CHAR_LENGTH(username) BETWEEN 4 AND 20),
    CONSTRAINT ck_user_normalized_length CHECK (CHAR_LENGTH(username_normalized) BETWEEN 4 AND 20),
    CONSTRAINT ck_user_nickname CHECK (CHAR_LENGTH(nickname) BETWEEN 1 AND 20),
    CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_user_admin_active CHECK (role <> 'ADMIN' OR status = 'ACTIVE'),
    CONSTRAINT ck_user_auth_version CHECK (auth_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE file_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT NOT NULL,
    storage_key VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    mime_type VARCHAR(32) COLLATE utf8mb4_0900_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    state VARCHAR(16) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT 'STAGING',
    created_at DATETIME(6) NOT NULL,
    ready_at DATETIME(6) NULL,
    unreferenced_since DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_storage_key (storage_key),
    KEY idx_file_uploader (uploader_id, id),
    KEY idx_file_cleanup (state, unreferenced_since, id),
    KEY idx_file_staging (state, created_at, id),
    CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_file_size CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_file_dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_file_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_file_state CHECK (state IN ('STAGING', 'READY', 'DELETING', 'DELETED')),
    CONSTRAINT ck_file_ready CHECK (state <> 'READY' OR ready_at IS NOT NULL),
    CONSTRAINT ck_file_deleted CHECK (
        (state = 'DELETED' AND deleted_at IS NOT NULL)
        OR (state <> 'DELETED' AND deleted_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

ALTER TABLE app_user ADD CONSTRAINT fk_user_avatar
    FOREIGN KEY (avatar_file_id) REFERENCES file_asset (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    title VARCHAR(50) NOT NULL,
    body TEXT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_note_feed (deleted_at, published_at DESC, id DESC),
    KEY idx_note_author (author_id, deleted_at, published_at DESC, id DESC),
    KEY idx_note_published (published_at, id),
    CONSTRAINT fk_note_author FOREIGN KEY (author_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_note_title CHECK (CHAR_LENGTH(title) BETWEEN 1 AND 50),
    CONSTRAINT ck_note_body CHECK (CHAR_LENGTH(body) BETWEEN 1 AND 5000),
    CONSTRAINT ck_note_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE note_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    sort_order TINYINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_image_file (note_id, file_id),
    UNIQUE KEY uk_note_image_order (note_id, sort_order),
    KEY idx_note_image_file (file_id, note_id),
    CONSTRAINT fk_note_image_note FOREIGN KEY (note_id) REFERENCES note (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_note_image_file FOREIGN KEY (file_id) REFERENCES file_asset (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_note_image_order CHECK (sort_order BETWEEN 1 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE note_like (
    user_id BIGINT NOT NULL,
    note_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, note_id),
    KEY idx_note_like_note (note_id, user_id),
    CONSTRAINT fk_note_like_user FOREIGN KEY (user_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_note_like_note FOREIGN KEY (note_id) REFERENCES note (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE `comment` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    root_comment_id BIGINT NULL,
    target_comment_id BIGINT NULL,
    reply_to_user_id BIGINT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_comment_page (note_id, root_comment_id, deleted_at, created_at, id),
    KEY idx_comment_root (root_comment_id, id),
    KEY idx_comment_target (target_comment_id),
    KEY idx_comment_author (author_id),
    KEY idx_comment_reply_user (reply_to_user_id),
    CONSTRAINT fk_comment_note FOREIGN KEY (note_id) REFERENCES note (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_root FOREIGN KEY (root_comment_id) REFERENCES `comment` (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_target FOREIGN KEY (target_comment_id) REFERENCES `comment` (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_comment_reply_user FOREIGN KEY (reply_to_user_id) REFERENCES app_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_comment_content CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 500),
    CONSTRAINT ck_comment_shape CHECK (
        (root_comment_id IS NULL AND target_comment_id IS NULL AND reply_to_user_id IS NULL)
        OR (root_comment_id IS NOT NULL AND target_comment_id IS NOT NULL
            AND reply_to_user_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
