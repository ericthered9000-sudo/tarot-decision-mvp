# Payment Integration Setup Guide

**Goal:** $6.99 one-time purchase via RevenueCat + Stripe (avoids 30% app store cut)

---

## What's Done ✅

- [x] RevenueCat SDK installed (`@revenuecat/purchases-js`)
- [x] Payment config template created (`payment-config.example.js`)
- [x] Paywall UI component created (`paywall-component.html`)
- [x] Capacitor Android build ready

---

## Manual Setup Steps (You Need to Do)

### Step 1: Create RevenueCat Account

1. Go to https://app.revenuecat.com
2. Sign up (free tier supports up to $2.5k/month)
3. Create new project: "Tarot Decision"
4. Add platform: **Web** (not iOS/Android - we're using web payments)

### Step 2: Configure Stripe in RevenueCat

1. In RevenueCat dashboard: Settings → Integrations → Stripe
2. Connect your Stripe account (or create one)
3. Enable "One-time purchases" (not just subscriptions)

### Step 3: Create Product in RevenueCat

1. Go to Products → Create Product
2. **Product ID:** `tarot_lifetime_access`
3. **Name:** "Lifetime Access"
4. **Type:** One-time purchase
5. **Price:** $6.99 USD
6. **Entitlement:** `lifetime_access`

### Step 4: Get API Keys

1. In RevenueCat: Project Settings → API Keys
2. Copy **Public API Key** (starts with `pk_...`)
3. Create `payment-config.js` from template:
   ```bash
   cp payment-config.example.js payment-config.js
   ```
4. Edit `payment-config.js` and paste your key:
   ```javascript
   revenueCat: {
     apiKey: 'pk_live_xxxxxxxxxxxxx', // Paste your key here
     appId: 'com.keystonedigital.tarot'
   }
   ```

### Step 5: Wire Paywall into App

Add this to `index.html` before `</body>`:

```html
<!-- Include paywall component -->
<script type="module">
  import { initRevenueCat, hasPurchased, purchaseLifetimeAccess } from './payment-config.js';
  
  // Make functions globally available for paywall
  window.initRevenueCat = initRevenueCat;
  window.hasPurchased = hasPurchased;
  window.purchaseLifetimeAccess = purchaseLifetimeAccess;
</script>

<!-- Paste paywall-component.html content here -->
```

Then in your `drawCards()` function, add:

```javascript
// After successful reading
incrementReadingCount(); // This shows paywall when limit reached
```

### Step 6: Test Purchase Flow

1. Enable test mode in RevenueCat (Settings → Storefronts → Test mode)
2. Use Stripe test card: `4242 4242 4242 4242` (any future expiry, any CVC)
3. Verify purchase completes and unlocks features
4. Disable test mode before launch

---

## Free vs Paid Features

**Free Tier:**
- 1 reading per day
- Three-card spread only
- No history saving

**Paid Tier ($6.99 one-time):**
- Unlimited readings
- All spread types (3-card, Celtic Cross, Relationship)
- Save reading history
- Priority support

---

## Alternative: Stripe Direct (No RevenueCat)

If you want to skip RevenueCat and go direct:

1. Use Stripe Payment Links
2. Create product in Stripe Dashboard
3. Generate payment link
4. On successful payment, redirect to `/success?session_id=xxx`
5. Verify session with Stripe API, grant access

**Pros:** Simpler, no RevenueCat dependency
**Cons:** More manual work, no built-in entitlement management

---

## Files Created

| File | Purpose |
|------|---------|
| `payment-config.example.js` | Template for API keys + config |
| `paywall-component.html` | Paywall UI + purchase logic |
| `PAYMENT_SETUP.md` | This guide |

---

## Next Steps

1. **Do the manual setup** (Steps 1-4 above)
2. **Test locally** before deploying
3. **Deploy to Vercel** with payment config
4. **Test on real device** with test card
5. **Flip the switch** when ready to monetize

---

**Questions?** RevenueCat docs: https://www.revenuecat.com/docs
