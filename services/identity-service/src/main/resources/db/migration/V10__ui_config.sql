-- Everything an administrator customizes about the look and wording of the portal (theme, menus per role, dashboard
-- cards, login text, renamed words) as one JSON document. The browser reads and applies it; the server only stores it.
ALTER TABLE institute_settings ADD COLUMN ui_config TEXT NOT NULL DEFAULT '{}';
