Decision Tarot - New Package & Org Launch

Package name changed from com.keystonedigital.tarot (old personal account) to:
  com.keystonedigital.decisiontarot

New keystore generated at android/tarot-release-key.keystore
  Password: tar0tK3yst0n3!2026
  Key alias: tarot-key
  NOTE: PKCS12 format - storepass == keypass (same password for both)
build.gradle uses: KEYSTORE_PASSWORD env var for both store and key password

Build status: Signed AAB built 2026-05-25 - 9.1MB at android/app/build/outputs/bundle/release/app-release.aab
Version: 4.5 (code 50), minSdk 23, targetSdk 35, BillingClient 8.3.0 via RevenueCat v13.1.2

Products needed in new Play Console listing:
  - tarot_monthly ($9.99/mo)
  - tarot_yearly ($79.99/yr)

RevenueCat setup needed:
  - Entitlement: premium
  - Both products linked to premium
  - Default offering with both packages

Fastlane: Appfile already has new package name. Service account is fastlane-play-store@keystone-play-store-492616.iam.gserviceaccount.com.
  To deploy: ssh hermes, cd ~/projects/decision-tarot, KEYSTORE_PASSWORD='tar0tK3yst0n3!2026' fastlane deploy
