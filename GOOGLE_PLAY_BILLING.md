# Google Play Billing — Direct Integration (No RevenueCat)

**Status:** Implemented ✅

Decision Tarot uses a **custom Capacitor plugin** that talks directly to Google Play Billing. No third-party billing SDK, no RevenueCat dashboard, no external API keys. Just Google Play Console + the plugin.

This is the standard approach for all Keystone Digital apps going forward.

---

## How It Works

| Component | Details |
|-----------|---------|
| Plugin | `GooglePlayBillingPlugin.java` — custom Capacitor plugin in the app's Android source |
| SDK | Google Play Billing Library (via Android dependency) |
| Products | Created directly in Google Play Console |
| Entitlements | Checked locally via `billing.isPremium()` — queries Google Play for active subscriptions |
| Fee | 15% Play Store fee (standard for small developers) |

### Pricing Tiers

| Tier | Product ID | Price | Type | Features |
|------|-----------|-------|------|----------|
| **Free** | — | $0 | — | 1 daily 1-card draw + encyclopedia |
| **Monthly** | `tarot_monthly` | $9.99/mo | Subscription | Unlimited readings, all spreads, AI insights, history, journal |
| **Yearly** | `tarot_yearly` | $79.99/yr | Subscription | Everything in monthly (~$6.67/mo, 33% off) |

Both monthly and yearly unlock the same `premium` entitlement — the difference is just billing period and price.

---

## Plugin API

The plugin is registered as `GooglePlayBilling` on the Capacitor bridge. Available methods:

### `init()`
Connects to Google Play Billing. Must be called before any other method.

### `isPremium()`
Queries Google Play for active subscriptions. Returns `{ isPremium: boolean }`. Any active subscription = premium.

### `purchase({ productId, offerToken? })`
Launches the Google Play purchase flow for the given product ID. Returns `{ success, productId, purchaseToken, orderId, isAutoRenewing }` or `{ success: false, cancelled: true }`.

### `queryProducts({ productIds: [] })`
Fetches product details (price, title, description, offer token) from Google Play for the given product IDs. All products must be subscription type.

### `restorePurchases()`
Queries Google Play for existing purchases. Returns `{ products: [], success: true }`.

---

## Setup (Google Play Console Only)

1. Go to [Google Play Console](https://play.google.com/console)
2. Select app: **Decision Tarot** (`com.keystonedigital.tarot`)
3. Navigate to: **Monetize → Products → In-app products**

**Create Product 1 — Monthly Subscription:**
- Product ID: `tarot_monthly`
- Name: Monthly Subscription
- Price: $9.99/mo
- Type: Subscription (auto-renewable)
- Save then Activate

**Create Product 2 — Yearly Subscription:**
- Product ID: `tarot_yearly`
- Name: Yearly Subscription
- Price: $79.99/yr
- Type: Subscription (auto-renewable)
- Save then Activate

No RevenueCat configuration needed. No API keys to manage. The plugin talks directly to Google Play.

---

## JS Integration (index.html)

```javascript
// Initialize
await window.Capacitor.Plugins.GooglePlayBilling.init();

// Check premium status
const result = await window.Capacitor.Plugins.GooglePlayBilling.isPremium();
const isPremium = result.isPremium;

// Purchase
const result = await window.Capacitor.Plugins.GooglePlayBilling.purchase({
  productId: 'tarot_monthly'
});

// Restore
const result = await window.Capacitor.Plugins.GooglePlayBilling.restorePurchases();
```

Premium status is also cached in `localStorage` as `premium_unlocked` for offline access.

---

## Access Code (Testing)

A secret access code ("Zeppelin") can be entered by tapping the title 5 times. This unlocks premium locally for testers without going through Google Play.

---

## Troubleshooting

### "Products not available"
- Product IDs in code must match Google Play Console exactly
- Products must be **Activated** in Google Play Console
- App must be on a testing track or published

### "Purchase cancelled"
- Normal when user cancels (not an error)

### "Billing not ready"
- `init()` must succeed before calling other methods
- Check that the plugin is registered in `MainActivity.java`

---

## Files

| File | Purpose |
|------|---------|
| `android/app/src/main/java/com/keystonedigital/decisiontarot/plugins/billing/GooglePlayBillingPlugin.java` | Custom Capacitor plugin (Google Play Billing) |
| `android/app/src/main/java/com/keystonedigital/decisiontarot/MainActivity.java` | Plugin registration |
| `public/index.html` | Payment logic + paywall UI |

## Reusable for Future Apps

This plugin is self-contained and can be copied to other Keystone Digital apps. Steps:
1. Copy `GooglePlayBillingPlugin.java` to the new app's Android source
2. Register it in `MainActivity.java`
3. Call `init()`, `isPremium()`, `purchase()` from JS
4. Create matching products in Google Play Console

No RevenueCat. No external dependencies. No API keys to manage.