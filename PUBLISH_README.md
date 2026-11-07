Decision Tarot - New Package & Org Launch

Package name changed from com.keystonedigital.tarot (old personal account) to:
  com.keystonedigital.decisiontarot

New keystore generated at android/tarot-release-key.keystore
  Password: tar0tK3yst0n3!2026
  Key alias: tarot-key
  NOTE: PKCS12 format - storepass == keypass (same password for both)
build.gradle uses: KEYSTORE_PASSWORD env var for both store and key password

Build status: Signed AAB built 2026-06-05 - 6.9MB at android/app/build/outputs/bundle/release/app-release.aab
Version: 5.0 (code 58), minSdk 23, targetSdk 35, BillingClient 8.3.0, custom billing plugin
Production: LIVE on Play Store (promoted from alpha 2026-06-05)

Products needed in new Play Console listing:
  - tarot_monthly ($9.99/mo)
  - tarot_yearly ($79.99/yr)

RevenueCat setup needed:
  - Entitlement: premium
  - Both products linked to premium
  - Default offering with both packages

Fastlane: Appfile already has new package name. Service account is fastlane-play-store@keystone-play-store-492616.iam.gserviceaccount.com.
  To deploy: ssh hermes, cd ~/projects/decision-tarot, KEYSTORE_PASSWORD='tar0tK3yst0n3!2026' fastlane deploy
