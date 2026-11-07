# Data Safety Guide – Google Play Console

**App:** Decision Tarot  
**Category:** Lifestyle / Entertainment

---

## Overview

Decision Tarot is a tarot reading app that helps users make decisions through card readings. The app is designed with privacy-first principles, collecting minimal data and keeping all user content on-device.

---

## Data Collection Summary

| Data Type | Collected? | Sharing | Required? |
|-----------|------------|---------|----------|
| Reading History (local) | ✅ Yes | No (on-device only) | Optional |
| Payment Info (RevenueCat) | ✅ Yes | Yes (RevenueCat/Stripe) | Yes (for purchases) |
| Account/Profile | ❌ No | — | N/A |
| Location | ❌ No | — | N/A |
| Device ID | ❌ No | — | N/A |
| Analytics/Tracking | ❌ No | — | N/A |
| Advertising ID | ❌ No | — | N/A |

---

## Detailed Data Practices

### 1. Reading History (Local Storage)

- **What is collected**: Tarot card selections, reading timestamps, user notes (if any)
- **How it's used**: To display past readings to the user
- **Storage**: Locally on the device only
- **Sharing**: None – data never leaves the device
- **Encryption**: Data stored in app's localStorage (not encrypted at rest by the app)
- **Deletion**: User can clear history from within the app, or by uninstalling the app

### 2. Payment Information (RevenueCat)

- **What is collected**: Subscription purchase status, transaction IDs, subscription expiration dates
- **How it's used**: To verify premium feature access and process refunds
- **Sharing**: Data is shared with RevenueCat (which may share with Stripe/Apple/Google for payment processing)
- **Encryption**: Handled by RevenueCat's PCI-DSS compliant infrastructure
- **Deletion**: Contact RevenueCat or developer to request removal

---

## Encryption

- **In Transit**: All network communications with RevenueCat use HTTPS/TLS encryption
- **At Rest**: Reading history is stored in the app's localStorage. The app does not implement additional encryption at rest; this is managed by the device's operating system
- **Payment Data**: Handled entirely by RevenueCat using their PCI-DSS compliant systems

---

## Data Retention

- **Reading History**: Retained until the user deletes it or uninstalls the app
- **Subscription Data**: Managed by RevenueCat per their retention policies
- **No Expiration**: There is no automatic deletion schedule for local data

---

## User Controls

Users can:
- **View History**: Access all past readings within the app
- **Delete History**: Clear all reading history via the app settings
- **Stop Subscription**: Cancel auto-renewal via the app store (Google Play / App Store)
- **Request Deletion**: Contact developer for data deletion requests

---

## Third-Party Services

| Service | Data Shared | Purpose |
|---------|-------------|---------|
| RevenueCat | Purchase info, subscription status | Payment processing & subscription management |

RevenueCat's Privacy Policy: https://www.revenuecat.com/privacy/

---

## Compliance Notes

- **No Personal Identifiers**: App does not require account creation; no names, emails, or phone numbers collected
- **No Tracking**: No analytics, crash reporting, or ad SDKs integrated
- **No Data Sales**: We do not sell user data to any third parties
- **Children**: App does not target children; no data collected from users under 13

---

## Contact

For data deletion requests or privacy questions, contact the developer.