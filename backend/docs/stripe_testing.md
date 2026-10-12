# Stripe Testing

## Local test setup

Use the config file that the running jar reads:

- `build/libs/config.json`

Set Stripe to test mode in that file:

- `secretKey`: `sk_test_...`
- `publishableKey`: `pk_test_...`
- `webhookSecret`: the `whsec_...` from `stripe listen`
- `prices.lifetime.priceId`: `""` or a test-mode `price_...`
- `prices.monthly.priceId`: `""` or a test-mode `price_...`

Leaving the `priceId` values empty is fine for local testing. The backend will create test prices automatically.

## Start the webhook listener

Run this in a separate terminal:

```bash
stripe listen --forward-to localhost:3001/api/payments/webhook
```

Copy the printed `whsec_...` into `build/libs/config.json`.

Important:

- Keep the listener running while testing.
- If you restart `stripe listen`, update `webhookSecret` with the new `whsec_...`.

## Run the backend

Restart the backend after any config change so it reloads Stripe settings.

## Test a lifetime purchase

Create a checkout session from the app or call:

```bash
curl -X POST http://localhost:3001/api/payments/lifetime ^
  -H "Authorization: Bearer YOUR_TOKEN"
```

Open the returned Stripe Checkout URL and pay with a Stripe test card:

```text
4242 4242 4242 4242
```

Use any future expiry date, any CVC, and any ZIP/postal code.

Expected result:

- Stripe CLI shows `checkout.session.completed`
- the webhook returns `200`
- the user plan updates to `lifetime`

## Test a monthly purchase

Create a checkout session from the app or call:

```bash
curl -X POST http://localhost:3001/api/payments/monthly ^
  -H "Authorization: Bearer YOUR_TOKEN"
```

Complete checkout with the same Stripe test card.

Expected result:

- Stripe CLI shows subscription-related events
- the webhook returns `200`
- the user plan updates to `premium`
- `stripe_subscription_id` is stored on the user

## What to check if it fails

- Wrong config file edited. Local runs use `build/libs/config.json`.
- Live Stripe keys or live `price_...` IDs are still in config.
- `webhookSecret` does not match the currently running `stripe listen` session.
- Backend was not restarted after config changes.
- Backend logs contain `[PAYMENT]` errors from the webhook handler.

## Verified flow

This local Stripe test flow was verified for both:

- lifetime checkout
- monthly subscription checkout
