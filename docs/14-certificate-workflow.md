# Certificate request and approval

A student no longer issues their own certificate. They ask; an ADMIN decides; the certificate exists only once issuance succeeds. Lives in certificate-service (V2 migration).

```
Student: Request  ->  PENDING  --Approve-->  APPROVED  --Issue-->  ISSUED
                         |                       |
                         +------ Reject (reason required) ------> REJECTED  (student may ask again)
ISSUED certificate --Revoke (admin, reason)--> REVOKED  (verifies as REVOKED)
```

## Rules enforced in the service (annotations are only a first gate)
- Request needs eligibility (the existing completion rule, checked against the owning services), no valid certificate for the course, and no other open (PENDING/APPROVED) request. A partial unique index backs the last rule in the database.
- The batch comes from the student's own enrolment, never the request body.
- Approve, reject, issue and revoke are ADMIN only. A trainer can list/open requests **only** if `itilms.certificate.trainer-access=true` (default off, read only, no eligibility checks). Students see only their own.
- Issue re-checks eligibility, writes the certificate, renders the PDF (proving it can be produced) and marks the request ISSUED in one transaction. A failure leaves the request APPROVED and no certificate.
- The admin's detail view re-checks eligibility live, since a payment may have been reversed after the request.

## API (all under `/api/certificates`)
| | |
|---|---|
| `POST /requests` `{courseId}` | student |
| `GET /requests/me` | student |
| `GET /requests?status=&courseId=&batchId=&q=&from=&to=` | admin (trainer if enabled) |
| `GET /requests/{id}` | admin, own student; includes live eligibility |
| `POST /requests/{id}/approve`, `/reject {reason}`, `/issue` | admin |
| `POST /` (direct issue), `POST /{id}/revoke`, `GET /` | admin (list: staff) |
| `GET /me`, `GET /{id}/pdf` | student own, staff |
| `GET /verify/{no}?code=` | public, rate-limited |

`POST /claim` (student self-issue) is removed.

## Verification, PDF, audit, notifications
- Public page `/verify/{certificateNo}?code=...` shows ID, student name, course, institute, issue date and VALID/REVOKED only. The code is required (anti-enumeration, see O5); the printed URL already contains it.
- The PDF prints the institute name, logo, signatory name/title (from Branding, docs/13), batch, issue date, certificate ID, verification code and URL.
- Audit (reporting audit log): `CERTIFICATE_REQUESTED / APPROVED / REJECTED / ISSUED / DOWNLOADED / REVOKED`. Notifications: request submitted (student) and new request (ADMIN role); approved, rejected (with reason) and issued (student, in-app + email).

## Departures from the spec, on purpose
- No `tenant_id` and no separate `certificate_audit_logs` / `certificate_templates` tables: isolation is per deployment (docs/13), audit goes to the platform's audit log, and the PDF layout is code driven by Branding rather than a stored template.
- Certificate numbers keep the existing `ITILMS-YYYY-NNNNNNN` format (globally unique, sequence-backed) rather than `CERT-...`.
- Re-validating a revoked certificate is not offered (the spec makes it conditional on an institute policy that does not exist yet).
- COORDINATOR no longer has a certificates page; the deciding role is ADMIN.
