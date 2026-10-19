# "Verify you are human" (Cloudflare Turnstile)

A checkbox on the **sign-in**, **registration** and **forgot-password** forms that stops scripts from guessing passwords or flooding the form. Lives in identity-service (`CaptchaVerifier`) and the frontend (`lib/use-captcha.tsx`).

## Why the server checks it
The widget alone proves nothing: a script can skip the page and call `/api/auth/login` directly. So the browser sends the widget's token with the form, and **identity-service checks that token with Cloudflare using the secret key** before it looks at the password. A token works once; after a failed submit the page asks for a fresh one.

If Cloudflare cannot be reached the request is **refused** (`503 CAPTCHA_UNAVAILABLE`), not waved through. A missing token is `422 CAPTCHA_REQUIRED`; a refused one is `422 CAPTCHA_FAILED`.

This sits on top of what was already there: the gateway's 5-requests-a-second limit on `/api/auth/**` and the lock-out after five failed sign-ins.

## Switching it on
Off by default, so a plain local run needs no keys. To turn it on:

1. Cloudflare dashboard -> Application security -> **Turnstile** -> Add widget. Mode **Managed**. Add every hostname the site is served from (and `localhost` for testing). Free, no card.
2. In `deploy/docker/.env`:
   ```
   CAPTCHA_ENABLED=true
   CAPTCHA_SITE_KEY=<the Site Key - public>
   CAPTCHA_SECRET=<the Secret Key - private>
   ```
3. Restart identity-service: `docker compose up -d --no-deps identity-service`.

With `CAPTCHA_ENABLED=true` both keys are required: **identity-service refuses to start** if either is empty, rather than quietly run unprotected.

The browser learns whether to show the widget, and with which public key, from `GET /api/auth/captcha-config` (`{enabled, siteKey}`). The secret never leaves the server. Changing the keys needs only an identity-service restart, no frontend rebuild.

## What the visitor sees
A "Verify you are human" box above the submit button. The button stays disabled until it is passed. If the script is blocked (an ad blocker, no internet) the form says so and cannot be submitted.

## Limits
- It slows down bots, it does not stop a determined human or a paid solving service.
- Turnstile only works on hostnames added to the widget, so a new domain must be added there.
- The check needs the visitor's browser to reach `challenges.cloudflare.com`.
