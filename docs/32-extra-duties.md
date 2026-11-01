# Extra duties: one person, more than one job

A person has one **main role** (Trainer, Coordinator, Finance, Placement...). An administrator can add **extra duties** on top: a trainer who also does the coordinator's work, a coordinator who also keeps the fees. The main role does not change and nothing is taken away; the duties are added, and withdrawn again the same way.

## What can be given

| Main role | Extra duties that can be added |
|---|---|
| Trainer, Coordinator, Finance, Placement | Coordinator, Finance, Placement (never the person's own main role) |
| Admin | none: an administrator already has every duty |
| Student | none |

Trainer is not an extra duty, because it needs a trainer profile (batches name the profile, not the login); a coordinator who should also teach needs a trainer profile first. Admin is never given as an extra duty.

Giving **Coordinator** gives all of the coordinator's rights: admissions, students, batches, courses, course requests, users (view), announcements, file and report downloads, ID cards, and the coordinator's notifications. It does not give the admin-only pages (Customize, Monitoring, Audit logs, changing users and duties).

## How an administrator does it

Users page, on a person's row, **Duties**. Tick what to add, untick what to take back, Save. The row then shows `+ Coordinator` badges, so it is visible who holds what. Only an ADMIN can do this (the button is not shown to anyone else), and every change is written to the audit log (`USER_DUTIES_CHANGED`: who, whom, before, after). The person gets a notification, "Your duties changed".

API: `PUT /api/users/{id}/duties` with `{"extraRoles": ["COORDINATOR"]}`; an empty list withdraws everything. It is ADMIN only. A student, an admin or a deleted account, a duty that is not on the list above, or the person's own main role as a duty, is refused with the reason.

## It applies at once, both ways

The access token is valid for 30 minutes, so waiting for it to expire would leave a withdrawn duty working for half an hour. Instead:

1. Identity keeps `users.roles_version`, a number that goes up every time a person's duties change, and puts it in every token it issues (`rv`), together with the duties (`xr`).
2. On a change, identity writes the new number to Redis under `rv:<userId>`.
3. The gateway, on every request, compares the token's number with the one in Redis (`RolesVersionCheck`). A token with an older number is answered with `401 PERMISSIONS_CHANGED`.
4. The web app already renews its token and repeats a request that got a 401. The renewal reads the person from the database, so the new token carries the new duties, and the answer to the repeated request is the one the new duties allow. The sidebar changes the same moment (the list of roles in the page's store is replaced by the renewal's answer). Nothing is asked of the person; they do not sign in again.

So a duty given or taken back applies on the person's next click. Someone who is not clicking sees nothing change until they do, and the notification bell's regular check usually triggers it within a minute.

If Redis cannot be reached the check is skipped for that request (so a cache outage does not lock everyone out); the change is still saved, the audit record says the gateway could not be told (`gatewayTold: false`), and the new duties apply when the token is next renewed, within 30 minutes.

## What reads the duties

- **Every endpoint** with `@PreAuthorize("hasAnyRole(...)")`: each duty is a Spring authority, so a person gets in through any of them. No endpoint was changed for this.
- **Checks in code** (`AppPrincipal`): `hasRole(role)` is true for the main role or an extra duty; `isStaff()` is true for ADMIN or COORDINATOR held either way; `isTrainer()` and the trainer profile id follow the **main** role only, because the profile belongs to it. A trainer who also coordinates therefore takes the coordinator's "can see every batch" path, and still has their own batches and trainer profile.
- **Announcements and notifications to a role** (new student, course request, fee overdue, announcements to Finance) reach the people who hold the role as an extra duty: the notification service's directory keeps the duties (`recipients.extra_roles`, from the `user-updated` event).
- **File rights** (receipts for Finance, resumes for Placement) follow the duty.
- **The web app** reads the list in the store (`state.roles`: main role first, then duties) for menus, route guards and buttons. `hasRole(roles, [...])` takes that list.

## Not covered

- **Customize (menu look)** is kept per main role: a trainer who is also a coordinator gets the trainer menu's order, labels and hidden items, with the coordinator pages added. Hidden items follow the main role.
- **Time limits**: a duty lasts until an administrator withdraws it; there is no end date.
- **Trainer as an extra duty** (a coordinator who teaches): needs a way to attach a trainer profile to an existing login. Not built.
- **Other services' own checks** that compare the main role directly would not see a duty. The code was searched for them (`.role()`, `Roles.X.equals`); the ones found were changed. A new check should use `principal.hasRole(...)` or `isStaff()`, never `role().equals(...)`.
- The services behind the gateway trust the gateway for the "duties changed" check; they only read the duties from the token. Anything that reaches a service directly, bypassing the gateway, is not covered by the instant change (services are not published outside the Docker network).

## Files

`identity-service`: `V13__extra_duties.sql`, `User` (extra roles, version), `UserService.updateDuties`, `RolesVersionStore`, `JwtIssuer`. `common-lib`: `AppPrincipal`, `JwtVerifier`, `UserCreatedEvent`. `api-gateway`: `RolesVersionCheck`, `JwtAuthenticationGatewayFilter`. `notification-service`: `V3__recipient_extra_roles.sql`, `DirectoryService`, `RecipientRepository`. Frontend: `stores/auth-store.ts`, `pages/users/users-page.tsx` (`DutiesDialog`), `components/sidebar-nav.tsx`, `routes/protected-route.tsx`.
