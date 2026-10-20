# Security

This document describes the protections built into CoreTrust Bank, and the ones that are deliberately out of scope.

## 1. Authentication

**Passwords**
- Hashed with **BCrypt** (salted, deliberately slow); plain passwords are never stored or logged. Request DTOs override `toString()` to mask passwords, OTPs and tokens.
- Policy: 8–64 characters with upper- and lower-case letters, a digit and a special character. The upper limit avoids BCrypt's silent 72-byte truncation.
- Staff-created users never receive a password: they get a one-time **setup link** (valid 24 h) and choose their own. "Forgot password" sends a **reset link** (valid 30 min). Only SHA-256 hashes of these tokens are stored.

**Tokens**

| | Access token | Refresh token |
|---|---|---|
| Format | JWT signed with HMAC-SHA256 | 256 random bits, Base64URL |
| Contents | user id (`sub`), role, session id (`sid`), unique id (`jti`), issuer, expiry | nothing (opaque) |
| Stored by server | no | SHA-256 hash only |
| Lifetime | 15 min (support: 30 min) | until the first access token expires |

**Session rules**
- **One active session per user.** A second login returns `409 SESSION_ALREADY_ACTIVE` unless the client sends `forceLogin: true`, which ends the old session immediately. The password is checked *before* this, so strangers can't probe who is online.
- **One extension** per session, up to a fixed end (30 min; support 60 min).
- **Rotation and reuse detection:** each refresh token works once; reusing a rotated token revokes the whole session.
- **Immediate revocation:** the JWT filter compares the token's `sid` with the user's current session on every request, so logout, force-login, password changes, password resets, email changes and locks take effect on the very next request.
- Login, refresh and validation errors have distinct codes (`INVALID_CREDENTIALS`, `TOKEN_EXPIRED`, `INVALID_TOKEN`, `SESSION_REVOKED`, `SESSION_EXPIRED`, `ACCOUNT_DISABLED`, `ACCOUNT_SETUP_PENDING`), so clients know whether to refresh, re-login or contact support.

## 2. Authorization

Two independent layers protect every sensitive endpoint:

1. **URL rules** in `SecurityConfig` (checked before the request body is even read), e.g. `/api/v1/admin/**` → ADMIN, `/api/v1/assisted/**` → SUPPORT or ADMIN.
2. **Method security** (`@PreAuthorize`) on controllers, plus explicit checks in services for the most critical rules.

**Roles and permissions**

| Role | Can |
|---|---|
| CUSTOMER | Own profile, accounts, beneficiaries, transfers, history, statements, notifications |
| SUPPORT | View customers' data; assisted operations with customer OTP; deposits; raise unfreeze and reversal requests and disputes; case updates |
| ADMIN | Everything support can view; approve/reject requests; lock/unlock; flag; staff and permission management; audit logs; maintenance |

Admins can grant individual support users extra permissions:

| Permission | Allows |
|---|---|
| `VIEW_AUDIT_LOGS` | Search the audit log |
| `MANAGE_USERS` | See staff users; lock/unlock **customers** |
| `FLAG_TRANSACTIONS` | Flag transactions; remove only flags they raised themselves |

**Privilege-escalation guards:** only admins (by role, never by permission) grant permissions; nobody can lock themselves; only admins can lock staff; the primary bootstrap admin can never be locked; nobody approves a request they raised (maker-checker).

## 3. Account ownership

- Customers never pass their own user id: it always comes from the token (`/users/me`, `@AuthenticationPrincipal`).
- Every customer lookup is scoped to the owner, e.g. `findByIdAndUserId(accountId, principal.id())`.
- A customer requesting another customer's account, beneficiary, transaction or notification gets **404, not 403**, so valid ids can't be discovered (IDOR protection).
- Transfers can only go **from** the customer's own account, and only **to** their own accounts or an ACTIVE CoreTrust beneficiary they saved.
- In transaction history, other parties' account numbers are masked (`XXXXXXXX4865`), and the counterparty never sees the other side's balance.
- Staff see customers only; plain support users cannot even see staff accounts.

## 4. Input validation

- **Bean Validation** on every request DTO: required fields, lengths, email format, Indian mobile numbers (`^[6-9]\d{9}$`), IFSC codes, account numbers (9–18 digits), amounts (`> 0`, two decimal places), OTP format (6 digits).
- **Business validation** in services and entities: balance and state checks, account limits, beneficiary rules, date ranges (statements ≤ 366 days), allowed sort fields.
- **Sort whitelisting** on transaction, user, notification and audit queries, so clients can't sort by sensitive nested properties.
- **Type errors** return readable 400s (including the list of allowed enum values) without exposing class names.
- **Output safety:** CSV statements neutralise spreadsheet formulas (CSV injection); user-supplied search text has `LIKE` wildcards escaped; all queries use bound parameters.

## 5. Sensitive operations

**Customer consent through OTP.** Staff-assisted operations require a code the customer receives by **email**:

| Property | Implementation |
|---|---|
| Code | 6 digits, `SecureRandom`, stored as a BCrypt hash |
| Lifetime / attempts | 5 minutes, 3 attempts |
| Binding | SHA-256 of the exact operation (customer, accounts, amount, ...). An OTP approved for ₹1,000 can't authorise ₹10,000; changed details count as a failed attempt and are audited separately |
| Single use | Consumed only if the protected operation commits; concurrent reuse is blocked by optimistic locking |
| Transparency | The email states exactly what is being approved; staff only ever see a masked address |

Protected this way: assisted account opening and closing, cash withdrawals, assisted transfers, assisted beneficiary addition, phone number changes, support-raised unfreeze requests, customer self-unfreeze, and email changes (code sent to the **new** address, plus the current password).

**Other safeguards**
- **Maker-checker:** unfreeze requests and reversals need a second person (an admin who didn't raise them). Admins cannot unfreeze directly.
- **Easy to lock, hard to unlock:** customers and support can freeze instantly; only the customer (for their own freezes, with OTP) or an approved request can unfreeze.
- **Alerts:** emails and in-app notifications for every debit and credit, beneficiary changes, freezes, locks, password resets, phone and email changes (the email-change alert goes to the **old** address).
- **Internal vs customer-facing reasons:** staff reasons stay in the audit log; customers see a safe default or a separate customer message.
- **No tipping off:** transaction flags are visible to staff only and never trigger customer messages.

## 6. Transfer security

- **Idempotency keys** (8–100 safe characters, scoped per user) with request fingerprints: retries never move money twice, and a key reused for a different request is rejected.
- **Atomic transactions:** debit, credit, ledger row, audit entry and outbox event commit together.
- **Optimistic locking** with bounded retries prevents lost updates under concurrency.
- **Limits:** ₹2,00,000 per transfer; frozen accounts can't send; closed accounts can't send or receive.
- **Monitoring:** a Kafka risk consumer auto-flags transfers of ₹1,00,000 or more and records a risk alert after 5 failed transfers by one user within 10 minutes.

## 7. Abuse prevention

**Rate limits** (Redis, fixed windows, keys hashed so no personal data is stored in Redis):

| Policy | Limit |
|---|---|
| Login attempts per IP | 30 per 15 min |
| Failed logins per email | 5 per 15 min, then blocked until the window ends; reset on success |
| OTP requests per customer | 5 per 15 min (covers every OTP flow) |
| Forgot-password per email / per IP | 3 per hour / 10 per hour |
| Beneficiary lookups per user | 20 per 10 min (prevents name harvesting) |

Exceeding a limit returns `429` with `Retry-After`. If Redis is unavailable, limits **fail open** (requests are allowed and a warning is logged), so a cache outage never locks customers out.

**Anti-enumeration:** login errors don't reveal whether an email exists; forgot-password returns the same message for every email and sends mail in the background (uniform timing); other customers' resources return 404.

**Token revocation:** see §1 — no separate blacklist is needed, because every access token is checked against the user's current session.

## 8. Audit logging

Every security-relevant action is written to `audit_logs` with the actor's id and email, IP address, entity, action and description. Highlights:

- Authentication: registration, successful and **failed** logins, session extension and termination, refresh-token reuse, logout, password and email changes, resets, setup links
- Money: transfers, deposits, withdrawals, reversals
- Accounts and users: creation, freeze/unfreeze, closing, locks, permission changes, phone changes
- OTPs: issued, verified, failed (including "correct code, changed details")
- Workflows: requests raised, approved, rejected, cancelled; disputes; flags
- Data access: statement downloads and emails, **searches of the audit log itself**

Design choices:
- Failures are recorded in a separate transaction (`REQUIRES_NEW`), so they survive the rollback of the failed operation.
- The table is append-only (`@Immutable`) and has no foreign keys, so history can't be altered or cascaded away.
- **Never logged:** passwords, OTP codes, tokens, password hashes. Internal Kafka event payloads exclude names, emails and internal notes.

## 9. Secrets management

- The `prod` profile reads every secret from **environment variables with no defaults**: database credentials, JWT signing key, bootstrap admin password, mail and Redis credentials. A missing variable stops startup with a clear error.
- The JWT key must be at least 256 bits; a weaker key is rejected at startup.
- Development defaults in `application.properties` exist only for local use; they must never be used in a real environment, and any secret that was ever committed should be rotated.
- `.env.example` lists the variables without values.

## 10. Production configuration

| Area | Setting |
|---|---|
| API documentation | Swagger UI and `/v3/api-docs` disabled in `prod` |
| Actuator | Only `health` and `info` exposed; health details shown to admins only; readiness depends on the database only |
| CORS | Allowed origins from configuration; explicit allowed and exposed headers; no credentials (tokens travel in headers, not cookies) |
| Forwarded headers | `server.forward-headers-strategy=native` behind a trusted load balancer, so audit logs and rate limits see real client IPs |
| Error responses | Stack traces and exception messages never returned; a `requestId` links the response to server logs |
| Security headers | Spring Security defaults: `X-Content-Type-Options`, `X-Frame-Options: DENY`, no-cache headers; HSTS once served over HTTPS |
| Sessions | Stateless (no HTTP session); CSRF disabled because no cookies carry credentials |
| Logging | JSON logs with request and user ids; SQL logging off |
| Shutdown | Graceful, so in-flight transfers finish |

## 11. Known limitations

- **No multi-factor authentication for login**, and no CAPTCHA; the per-email failure limit can be abused to lock a customer out for 15 minutes (a deliberate trade-off).
- **OTPs only by email**; a compromised mailbox defeats OTP protection.
- **HS256 with a single shared key** and no key-rotation mechanism; a multi-service setup would use asymmetric keys (RS256) with rotation.
- **Rate limiting fails open** when Redis is down, and uses fixed windows (short bursts at window boundaries are possible).
- **Self-service phone changes** are protected by an alert email, not an OTP.
- The **primary admin account cannot be locked**; it must be protected by a strong password (and, in a real deployment, network controls and MFA).
- **TLS is not terminated by the application**; HTTPS is expected from a reverse proxy or load balancer.
- **Kafka and Redis connections are unauthenticated** in the local setup; production would need credentials and TLS.
- **No KYC or anti-money-laundering screening** beyond high-value flagging and failure alerts.
- The **security cache** can, in a rare race, serve user details up to 60 seconds old after a change; eviction after commit makes this window very small.
