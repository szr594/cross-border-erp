-- Offline maintenance operation. Run against cross_erp_identity after Flyway V1.
-- Requires a temporary migration account with source SELECT and target write privileges.
-- Never run this script through a business service database account.
DELIMITER $$
CREATE PROCEDURE cross_erp_identity.import_legacy_identity()
BEGIN
    DECLARE source_count BIGINT;
    DECLARE target_count BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;
    SELECT COUNT(*) INTO target_count FROM cross_erp_identity.app_users;
    IF target_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target identity users must be empty';
    END IF;
    IF EXISTS (SELECT 1 FROM cross_border_erp.roles s LEFT JOIN cross_erp_identity.roles t
        ON s.id=t.id AND s.code=t.code WHERE t.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Role IDs or codes do not match';
    END IF;
    SELECT COUNT(*) INTO source_count FROM cross_border_erp.app_users;
    INSERT INTO cross_erp_identity.app_users
        (id,login_name,display_name,password_hash,status,must_change_password,failed_login_count,
         locked_until,last_login_at,created_at,updated_at)
    SELECT id,login_name,display_name,password_hash,status,must_change_password,failed_login_count,
         locked_until,last_login_at,created_at,updated_at FROM cross_border_erp.app_users;
    INSERT INTO cross_erp_identity.user_roles(user_id,role_id)
    SELECT user_id,role_id FROM cross_border_erp.user_roles;
    SELECT COUNT(*) INTO target_count FROM cross_erp_identity.app_users;
    IF source_count <> target_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'User count verification failed';
    END IF;
    COMMIT;
END$$
DELIMITER ;
CALL cross_erp_identity.import_legacy_identity();
DROP PROCEDURE cross_erp_identity.import_legacy_identity;
