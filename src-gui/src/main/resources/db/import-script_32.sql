INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (32, 32, CURRENT_TIMESTAMP);

INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(362, 'um_user_account_unlock', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission to unlock user account');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 362);
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(46, 'CONFIG_INVALID_LOGIN_ATTEMPT_COUNT'),
	(47, 'CONFIG_USER_ACCOUNT_UNLOCK_TIME'),
	(48, 'UNLOCK_USER_ACCOUNT');

INSERT INTO "APPLICATION_SETTING" (id, setting_type, setting_value) VALUES (3, 'invalid_login_attempt', '5');
INSERT INTO "APPLICATION_SETTING" (id, setting_type, setting_value) VALUES (4, 'user_account_unlock_time', '60');

INSERT  INTO "KILDA_STATUS" (status_id, STATUS_CODE, STATUS) VALUES 
	(3, 'LCK', 'Lock');
	

	

