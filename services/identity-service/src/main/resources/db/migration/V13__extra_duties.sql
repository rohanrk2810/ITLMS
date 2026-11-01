-- =====================================================================
-- Extra duties: an administrator can give a person more than their main role (a trainer who also does the
-- coordinator's work, a coordinator who also keeps the fees). The main role stays in users.role.
--
-- extra_roles is a comma-separated list of role names; the application only ever writes COORDINATOR, FINANCE and
-- PLACEMENT. roles_version goes up every time the duties change. It rides in the access token, and the gateway sends
-- a token whose number is older than the current one to be renewed, so a grant or a withdrawal takes effect at the
-- next request, not when the token expires.
--
-- (Numbered 13 because an earlier, withdrawn change used 12 on the running server; a fresh install simply skips it.)
-- =====================================================================
ALTER TABLE users
    ADD COLUMN extra_roles    VARCHAR(100),
    ADD COLUMN roles_version  INT NOT NULL DEFAULT 0;
