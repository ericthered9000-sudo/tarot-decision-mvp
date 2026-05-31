# Google Play Billing Integration Guide

**Status:** Implemented ✅

The Decision Tarot app now uses **Google Play Billing** via the RevenueCat Capacitor native plugin (`@revenuecat/purchases-capacitor`). This is fully compliant with Play Store policies — the 15% fee tier applies.

---

## What Was Changed

| Change | Details |
|--------|---------|
| Removed | `payment-config.js`, `payment-config.example.js`, `paywall-component.html` — old RevenueCat web SDK files |
| Removed | `PAYMENT_SETUP.md`, `REVENUECAT_SETUP.md` — old setup guides |
| Removed | `@revenuecat/purchases-js` — the web SDK npm package (bypassed Play Store billing) |
| Kept | `@revenuecat/purchases-capacitor` v6.1.2 — the **native** Capacitor plugin (uses Google Play Billing properly) |
| Rewritten | Payment engine in `public/index.html` — now supports 2 subscription tiers with free daily draw |
| New | Free tier: 1 free 1-card daily draw, 3/7-card spreads require subscription |
| New | Paywall UI with 2 selectable tiers (Monthly $9.99, Yearly $79.99) |
| New | This guide |

### Pricing Tiers

| Tier | Product ID | Price | Type | Entitlement | Features |
|------|-----------|-------|------|-------------|----------|
| **Free** | — | $0 | — | — | 1 daily 1-card draw + encyclopedia |
| **Monthly** | `tarot_monthly` | $9.99/mo | Subscription | `premium` | Unlimited readings, all spreads, AI insights, history, journal |
| **Yearly** | `tarot_yearly` | $79.99/yr | Subscription | `premium` | Everything in monthly (~$6.67/mo, 33% off) |

---

## Manual Setup Steps (You Need to Do)

### Step 1: Create Products in Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Select app: **Decision Tarot** (`com.keystonedigital.tarot`)
3. Navigate to: **Monetize** → **Products** → **In-app products**

**Create Product 1 — Monthly Subscription:**
- Click **Create product**
- **Product ID:** `tarot_monthly`
- **Name:** Monthly Subscription
- **Description:** Unlimited tarot readings, all spreads, AI insights
- **Price:** $9.99/mo
- **Type:** Subscription (auto-renewable)
- Click **Save** then **Activate**

**Create Product 2 — Yearly Subscription:**
- Click **Create product**
- **Product ID:** `tarot_yearly`
- **Name:** Yearly Subscription
- **Description:** Unlimited tarot readings at 33% off
- **Price:** $79.99/yr
- **Type:** Subscription (auto-renewable)
- Click **Save** then **Activate**

### Step 2: Configure RevenueCat Dashboard

1. Go to [RevenueCat Dashboard](https://app.revenuecat.com)
2. Select your project: **Decision Tarot** (or create one if not done)
3. Make sure the platform is **Google Play** (NOT Web)

#### Create Entitlements

1. Navigate to: **Entitlements** → **+ New**
2. Create `premium`:
   - **Identifier:** `premium`
   - **Description:** Premium access — unlimited readings, all spreads, AI insights, history, journal

#### Create Products in RevenueCat

For each product:

1. Navigate to: **Products** → **+ New**
2. **Product 1 — Monthly:**
   - **Identifier:** `tarot_monthly` (must match Google Play)
   - **Type:** Subscription (auto-renewable)
   - **Store:** Google Play
   - **Product ID:** `tarot_monthly` (same as Google Play)
   - Attach entitlement: `premium`

3. **Product 2 — Yearly:**
   - **Identifier:** `tarot_yearly` (must match Google Play)
   - **Type:** Subscription (auto-renewable)
   - **Store:** Google Play
   - **Product ID:** `tarot_yearly` (same as Google Play)
   - Attach entitlement: `premium`

#### Create Offering

1. Navigate to: **Offerings**
2. Create offering:
   - **Identifier:** `default`
   - **Description:** Default offering
3. Add packages (both):
   - **Package 1:** Identifier `tarot_monthly`, Package Type: Monthly, Product: `tarot_monthly`
   - **Package 2:** Identifier `tarot_yearly`, Package Type: Annual, Product: `tarot_yearly`
4. Click **Save**

### Step 3: Get API Keys

1. In RevenueCat: **Project Settings** → **API Keys**
2. Copy the **Google Play / Android** API key (starts with `goog_...`)
3. This key is already in `index.html`:
   ```javascript
   const REVENUECAT_API_KEY = 'goog_pfkOiYhnnEgDTPgSChROlrVFSpg';
   ```
   If you get a new key, update this line.

### Step 4: Test Purchase Flow

1. Create a **License Tester** account in Google Play Console:
   - Go to **Setup** → **License testing**
   - Add your Gmail address
   - Check **Licensed** for testing
2. On your test device:
   - Use the same Google account as the license tester
   - Install the app via internal testing track
3. Try the purchase flow — it should show all three tiers
4. Select each tier and verify the purchase completes

### Step 5: Verify Entitlements

After a test purchase:
1. Open RevenueCat Dashboard → **Customers**
2. Find your test user
3. Verify the entitlement (`lifetime_access` or `pro_plus`) shows as **Active**
4. Test that premium gates unlock correctly:
   - Unlimited readings
   - All spread types
   - History tracking
   - Dashboard charts
   - Reading journal
   - Outcome tracking

---

## App Architecture

### How It Works

```
User taps "Get Lifetime Access"
       ↓
RevenueCat Capacitor Plugin calls Google Play Billing
       ↓
Google Play shows purchase dialog (Play Store UI)
       ↓
Purchase completes → Google sends receipt to RevenueCat
       ↓
RevenueCat validates receipt, grants entitlement
       ↓
App queries entitlements via Purchases.getCustomerInfo()
       ↓
Premium features unlocked
```

### Entitlement Logic

```javascript
let isPremium = localStorage.getItem('premium_unlocked') === 'true';

// Check entitlements
const active = info.entitlements.active;
isPremium = !!active['premium'];

// Feature gates:
// - 1 daily 1-card draw: always free
// - unlimited readings: isPremium
// - all spreads (3-card, 7-card): isPremium
// - reading history: isPremium
// - AI readings: always free (hook to upgrade)
// - encyclopedia: always free
```

### Current API Key

```
goog_pfkOiYhnnEgDTPgSChROlrVFSpg
```

---

## Troubleshooting

### "Products not available"
- Google Play product IDs must match RevenueCat product IDs exactly
- Products must be **Activated** in Google Play Console
- Products must be in an **Offering** in RevenueCat
- App must be on an **internal testing track** (or published) for real purchases

### "Purchase cancelled"
- This is normal when user cancels (code: `PURCHASE_CANCELLED_ERROR`)
- Not an error — the paywall just closes

### "Purchase failed"
- Check RevenueCat Dashboard → **Logs** for details
- Verify API key is correct for Google Play
- Check Google Play Console → **Monetize** → **Products** for activation status

### "Already entitled"
- User already purchased the product
- Refreshed entitlements should unlock features automatically

---

## Before Launch Checklist

- [ ] Products created in Google Play Console (both: `tarot_monthly`, `tarot_yearly`)
- [ ] Products created in RevenueCat (both, matching IDs)
- [ ] Entitlement `premium` created in RevenueCat
- [ ] Products linked to entitlement
- [ ] Offering created with both packages
- [ ] Google Play license testers configured
- [ ] Test purchases work for both tiers
- [ ] Premium gates unlock correctly in app
- [ ] Free daily draw resets properly (1 per day)
- [ ] App is on internal testing track or production track

---

## Files

| File | Purpose |
|------|---------|
| `public/index.html` | All payment logic + paywall UI |
| `GOOGLE_PLAY_BILLING.md` | This guide |
| `node_modules/@revenuecat/purchases-capacitor/` | Native Capacitor plugin (Google Play Billing) |

## Questions?

RevenueCat Capacitor Plugin docs:
https://www.revenuecat.com/docs/getting-started/plugins/capacitor

Google Play Billing docs:
https://developer.android.com/google/play/billing
