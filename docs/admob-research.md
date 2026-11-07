# Decision Tarot — AdMob Monetization Research

**Card:** t_eef81233 · **Author:** Dave (research) · **Date:** 2026-10-27
**App:** `com.keystonedigital.decisiontarot` — production v5.0.2 (versionCode 62)
**Scope:** research only. No code changes were made, no AdMob account was created.

---

## 1. RECOMMENDED SETUP

### 1.1 Plugin + exact version

```
npm install @capacitor-community/admob@7.2.0 --save-exact
npx cap sync android
```

**Pin it exactly.** `@capacitor-community/admob@7.2.0` (published 2025-10-25) is the **last
stable release compatible with Capacitor 7**. The current `latest` on npm is **8.2.1**
(2026-10-06) and it declares `"@capacitor/core": "^8.0.0"` — installing it into this repo
(which is on `@capacitor/core` **7.6.5**) breaks the build. Verified from the npm registry
version map: the 7.x line ends at `7.2.0` stable, with `7.2.1-0` being a prerelease tag only.

Do **not** use `admob-plus` (see §2 for the full comparison): its newest publish is
`2.0.0-alpha.18` from **2024-11-15** — still an alpha, Cordova-shim based, effectively
unmaintained for Capacitor 7.

### 1.2 Integration outline (one paragraph)

Add the plugin at the version above, then wire four things in this order: (1) put the AdMob
Android **App ID** in `android/app/src/main/AndroidManifest.xml` as
`<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="@string/admob_app_id"/>`
with the value in `res/values/strings.xml` — the SDK crashes at startup without it; (2) on app
boot call `AdMob.initialize()` **then** `AdMob.requestConsentInfo()`, and only if
`consentInfo.canRequestAds === false` call `AdMob.showConsentForm()` (this is the UMP gate for
EEA/UK/**Switzerland** traffic, and it must run **before** any ad request); (3) gate the banner
behind entitlement — when
the user is a free user and consent allows ads, call
`AdMob.showBanner({adId, adSize: BannerAdSize.ADAPTIVE_BANNER, position: BannerAdPosition.TOP_CENTER, margin: <statusBarHeightDp>, isTesting: __DEV__})`
and call `AdMob.hideBanner()` (or `removeBanner()`) the moment entitlement flips to paid; (4) since
the banner is a **native view layered over the WebView**, subscribe to
`BannerAdPluginEvents.SizeChanged` and offset the app shell by the reported height so the banner
cannot cover the app's own header — offset a fixed, non-scrolling shell (`top: <height>px;
bottom: 0; overflow-y: auto`), **not** a `padding-top` on a scrollable body, and use the dp height
verbatim without a `devicePixelRatio` multiply (both corrected after QA defects DEF-1/DEF-2 —
§2.2). The change is web-side JS/CSS plus ~6 lines of
Android XML for the banner itself, **plus the billing-plugin change in §4.4** if `tarot_lifetime`
ships at the same time — no backend, no new service, no new third-party account beyond AdMob.

### 1.3 SKU recommendation

Keep both existing subs and **add one non-consumable lifetime SKU** — the lifetime purchase is
the real revenue change, the banner is what gives it a reason to exist (§4.2, §5.4).

| SKU | Type | Price | Grants |
|---|---|---|---|
| `tarot_monthly` (exists) | auto-renewing sub | $9.99/mo | no ads + all spreads |
| `tarot_yearly` (exists) | auto-renewing sub | $79.99/yr | no ads + all spreads |
| **`tarot_lifetime`** (new) | **one-time product (INAPP), non-consumable** | **$19.99** | no ads + all spreads, forever |

**Correction to the card:** the yearly SKU in the shipped code and in
`PLAY_STORE_BILLING_STATUS.md` is **`tarot_yearly`**, not `yearly_base`. Verify Play Console
matches `tarot_yearly` before anything else — a mismatch means yearly purchases fail silently,
which is a candidate explanation for part of the zero-sales picture.

**Implementation caveat:** `GooglePlayBillingPlugin.java` hardcodes
`BillingClient.ProductType.SUBS` in all four query paths, so a one-time product will not appear
until the plugin also queries `ProductType.INAPP`. Detail in §4.4 — this must ship in the same
release as the Play Console product.

### 1.4 James's manual checklist (dashboard work — Dave cannot do these)

1. **AdMob account** — sign in at https://admob.google.com with the same Google account that
   owns the Play Console. Accept the AdSense/AdMob terms (payment info required → this is why
   it must be you).
2. **Register the app** — Apps → Add app → "Android" → search Play Store for
   `Decision Tarot` / paste `com.keystonedigital.decisiontarot`, or add manually if the listing
   is not yet indexed. Wait for AdMob to link it to the store listing.
3. **Link AdMob ↔ Play/Firebase (recommended, not required)** — AdMob app settings → "App
   Store" link. Needed only for AdMob-side analytics of store metrics.
4. **Create one ad unit** — Ad units → Add ad unit → **Banner** → name it
   `decisiontarot_banner_top` → copy the `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN` ID.
5. **Copy the App ID** — App settings → App ID (`ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN`,
   note the `~` vs `/` difference). This goes into `strings.xml`, not the manifest.
6. **Consent / GDPR message** — Privacy & messaging → GDPR (EU consent) → create a message,
   publish it. UMP is inert until a message exists in that section.
7. **US states consent** — Privacy & messaging → "US state privacy law" message (CPRA etc.).
   Same screen family; publish it too.
8. **Payment/tax profile** — Payments → verify identity + tax info. Ads accrue revenue from
   day one but **AdMob will not pay out below $100** (see §5).
9. **Play Console → Monetize → Products → In-app products → Create product** — ID
   `tarot_lifetime`, name "Tarot Lifetime", price **$19.99**, then **Activate**. (One-time
   products are a different screen from Subscriptions; you cannot create them from the
   Subscriptions page.)
10. **Play Console → Monetize → Products → Subscriptions** — confirm the yearly SKU is
   **`tarot_yearly`** and that it matches the code. Fix here if it isn't.
11. **Play Console → "Contains ads"** — App content → Ads → **Yes, contains ads** (declaration
    change re-review; usually hours).
12. **Play Console → Data safety** — add the AdMob rows from §3.2 before shipping the build.
13. **Privacy policy** — update `public/privacy-policy.html` to disclose ad/device data
    (§3.1). A live app serving ads with an ad-free privacy policy is a policy violation.
14. **Play Console → new release** — upload the AAB with the plugin + lifetime SKU wiring,
    versionCode 63+.

---

## 2. Plugin choice: `@capacitor-community/admob` vs `admob-plus`

*(decision detail — evidence gathered 2026-10-27 from the npm registry and the published package contents)*

| | `@capacitor-community/admob` | `admob-plus` |
|---|---|---|
| Latest version | **8.2.1** (2026-10-06) | **2.0.0-alpha.18** (2024-11-15) |
| Latest **stable** | 8.2.1 | *none — 2.x is all alpha* |
| Last release compatible with Capacitor 7 | **7.2.0** (2025-10-25), requires `@capacitor/core ^7.0.0` | Cordova shim (`admob-plus-cordova@2.0.0-alpha.19`, 2024-11-15) |
| Integration model | Native Capacitor plugin (`capacitor.android.src: android`) | Cordova plugin consumed via Capacitor's Cordova compat layer |
| UMP (Google User Messaging Platform) | **Built in** — `requestConsentInfo`, `showConsentForm`, `showPrivacyOptionsForm`, `resetConsentInfo`; native dep `com.google.ump:user-messaging-platform:3.1.0` | Not provided by the plugin; you integrate UMP yourself or use a separate Cordova plugin |
| Native SDK | `com.google.android.gms:play-services-ads:24.7.+` | pinned inside the Cordova plugin, no documented override |
| `minSdkVersion` | 23 (same as this app) | Cordova-era minimums |
| `compileSdkVersion` | 35 default, overridable from the root project | via Cordova compat |
| License / maintenance | MIT, active (commits within the last 3 weeks) | MIT, no release in ~2 years |

**Recommendation: `@capacitor-community/admob@7.2.0`.** Reasons, in order of weight:

1. **UMP is the deciding factor.** EEA/UK traffic legally requires a consent gate, and this
   plugin ships the UMP calls *and* the native UMP dependency. With `admob-plus` you would be
   writing the consent flow yourself on top of an unmaintained Cordova alpha — that is the
   single riskiest part of the whole feature.
2. **Native Capacitor plugin vs Cordova shim.** Capacitor 7's Cordova compat layer is a
   compatibility path, not the main path; every Cordova plugin is an extra failure mode on a
   `minSdk 23` / `compileSdk 36` app.
3. **`admob-plus@2.x` has been in alpha since 2023.** Two years without a stable release is not
   something to put in a released production app at versionCode 62.
4. **API surface is exactly what this feature needs** (verified from `dist/esm/*.d.ts`, v7.2.0):
   - `initialize(options?: AdMobInitializationOptions)` — supports `testingDevices`,
     `initializeForTesting`, `tagForChildDirectedTreatment`, `tagForUnderAgeOfConsent`,
     `maxAdContentRating`.
   - `showBanner(options: BannerAdOptions)` / `hideBanner()` / `resumeBanner()` / `removeBanner()`.
   - `BannerAdOptions` = `{ adId, isTesting?, margin?, npa?, adSize?, position? }`; `adSize`
     defaults to `ADAPTIVE_BANNER`, `position` to `TOP_CENTER`.
   - Events: `bannerAdSizeChanged`, `bannerAdLoaded`, `bannerAdFailedToLoad`, `bannerAdOpened`,
     `bannerAdClosed`, `bannerAdImpression`.
   - Consent: `requestConsentInfo({debugGeography, testDeviceIdentifiers, tagForUnderAgeOfConsent})`
     → `{status, isConsentFormAvailable, canRequestAds, privacyOptionsRequirementStatus}`,
     plus `showConsentForm()`, `showPrivacyOptionsForm()`, `resetConsentInfo()`.
   - `AdOptions.npa: true` forces non-personalized ads — useful if you ever want to serve ads to
     users who declined personalization without a second consent flow.

### 2.1 Build-toolchain caveat for THIS repo (read before installing)

The plugin's own `android/build.gradle` expects a newer Android toolchain than the app uses:

| | This repo (`android/`) | admob plugin 7.2.0 needs |
|---|---|---|
| Gradle wrapper | **8.4** | AGP 8.7.2 in its buildscript → wants Gradle **8.9+** |
| Android Gradle Plugin | **8.3.0** | 8.7.2 |
| Kotlin | **1.9.0** | 2.1.20 |
| Java target | — | `sourceCompatibility/targetCompatibility = 21`, `jvmTarget = 21` |
| compileSdk | 36 | 35 default (root value wins — fine) |
| minSdk | 23 | 23 (fine) |

JDK on this machine is **OpenJDK 21.0.12** (`/usr/lib/jvm/java-21-openjdk-amd64`), so Java 21 is
available; `JAVA_HOME` is **unset**, which Android Studio/Gradle may resolve to something else.

Expect a build-fix pass. Cheapest-to-most-invasive order:
1. Try the build as-is (`cd android && ./gradlew assembleDebug`) — mixed-AGP builds sometimes
   link cleanly because the plugin's `buildscript` is per-project.
2. If it fails on `jvmTarget 21` / Kotlin metadata: bump the repo to **AGP 8.7.x + Gradle 8.9+ +
   Kotlin 2.1.20** in `android/build.gradle` + `gradle-wrapper.properties`, keeping Capacitor 7.
3. Only if that cascades: upgrade Capacitor 7 → 8 and use `@capacitor-community/admob@8.2.1`
   (this is a separate card's worth of work — Capacitor major upgrades touch `MainActivity`,
   `variables.gradle` and every plugin).

`android/app/google-services.json` already exists and `google-services:4.3.15` is on the root
classpath, so the AdMob App ID can alternatively be supplied through Firebase — **don't**. Keep
it in `strings.xml` as the plugin README specifies; fewer moving parts, and the Firebase file's
App ID is not an AdMob ID.

### 2.2 Capacitor WebView pitfalls to plan for

- **The banner is a native Android view on top of the WebView.** It does not push content; it
  covers it. Listen for `bannerAdSizeChanged` (returns `{width, height, isAdaptive}` in **dp**,
  not px) and inset the app shell by that height.
  **The height is 1:1 with CSS px — use it verbatim: `inset = height`.**
  Do **not** multiply by `devicePixelRatio`. The page is laid out at
  `width=device-width, initial-scale=1`, so in an Android WebView **1 CSS px already equals
  1 dp**; `height * devicePixelRatio` double-converts (a 64dp banner became a 168px inset on a
  dpr-2.625 device = ~104 CSS px of dead space under the ad). *(Corrected 2026-10-27 — this
  formula was wrong in the original version of this doc and shipped as QA defect DEF-1,
  card `t_e4430cd7`.)*
- **Reserve the band, do not pad the scroller.** A `padding-top` on a scrollable `<body>` leaves
  the padding inside the scroll container, so scrolled content still slides *behind* the native
  banner (QA defect DEF-2). The implemented pattern in `public/index.html` is a fixed,
  non-scrolling shell: `html { overflow: hidden }` plus
  `body { position: fixed; top: var(--admob-banner-inset); left/right: 0; bottom: 0;
  overflow-y: auto; padding: 20px }`. The scroll viewport then starts exactly at the ad's bottom
  edge and the ad can never have content behind it (see §3.4 risk 2).
- **Status bar overlap.** `margin: 0` at `TOP_CENTER` puts the banner under the status bar on
  devices with a notch. Either pass `margin` = status-bar height (Capacitor `StatusBar` plugin's
  `StatusBarInfo.height`, or `SafeArea` insets) or accept the overlap — the plugin has no
  automatic safe-area handling. Test on a notch device.
- **Two different "top" surfaces.** The app's own header will shift down when the banner appears;
  if that header is `position: fixed`, padding on `<body>` won't move it — pad on the fixed
  element itself or wrap the app shell.
- **`position` default ambiguity.** The enum comment says `BOTTOM_CENTER` is the default, the
  option docstring says `TOP_CENTER`. Always pass `position` explicitly.
- **Test ads on a real device, not an emulator, and not with a live ad unit.** Use
  `isTesting: true` (Google's test banner ID) or `initializeForTesting` + `testingDevices`.
  Clicking your own live ads is the fastest way to get an AdMob account suspended.
- **Consent-then-banner ordering is mandatory.** Calling `showBanner` before `requestConsentInfo`
  resolves serves personalized ads to EEA users who never consented — a GDPR complaint, not a
  warning.
- **Don't show the banner on the paid path.** Check entitlement *before* the first ad request;
  showing then hiding creates a paid-user ad impression and a review-bomb waiting to happen.

---

## 3. AdMob + Play policy checklist

Everything below was read from Google's own help pages on 2026-10-27; URLs in §6.

### 3.1 Play Console declarations that change when you add ads

| Declaration | Where | What changes |
|---|---|---|
| **Ads → "Contains ads"** | App content → Ads | Must flip **No → Yes**. The article says explicitly: *"You must declare whether your app contains ads. This includes ads delivered through third-party ad software development kits (SDKs), display ads, native ads, or banner ads."* Banner via an ad SDK = **Yes**. The label is public on the store listing. Misrepresenting it *"is considered a violation of the Google Play policies and can result in the suspension of your app."* |
| **Data safety** | App content → Data safety | New rows required (§3.2). |
| **Content rating** | App content → Content ratings | **No change needed.** Google states the rating *"is specific to the content within your app. It does not include other features and practices, such as consumer agreements or ads."* But see §3.4 — the ad content itself must not be more mature than the app. |
| **Privacy policy** | App content → Privacy policy | Must be updated to disclose ad/device data. The existing `public/privacy-policy.html` mentions purchases but not ads or the ad ID — **it needs an edit before the ad build ships.** |
| **Target audience** | App content → Target audience | No change unless you want the app in the Families program. Do **not** opt into Families — Families ads rules are a much heavier burden. |

Practical sequencing: the "Contains ads" declaration and the Data safety change are declarations tied to the app, not the release, so do them **before** uploading versionCode 63. Google re-reviews declaration changes; the app stays live during that review.

### 3.2 Data safety rows AdMob requires

Google's own page for the Mobile Ads SDK lists exactly four data categories collected **and shared** automatically:

| SDK data (Google's wording) | Play Data safety row | Collected | Shared | Purposes |
|---|---|---|---|---|
| IP address (*"may be used to estimate the general location of a device"*) | **Location → Approximate location** | Yes | Yes | Advertising or marketing; Fraud prevention, security, and compliance |
| User product interactions (*"app launch, taps, and video views"*) | **App activity → App interactions** | Yes | Yes | Advertising or marketing; Analytics; Fraud prevention |
| Diagnostic information (*"app launch time, hang rate, and energy usage"*) | **App info and performance → Diagnostics** | Yes | Yes | Analytics; Fraud prevention |
| Device and account identifiers (*"Android advertising (ad) ID, app set ID"*) | **Device or other IDs → Device or other IDs** | Yes | Yes | Advertising or marketing; Analytics; Fraud prevention |

Two rules that trip people up, both quoted from Play's own Data safety page:

- Approximate location *"that is inferred, such as via IP address ... must be disclosed."* So you cannot skip the location row on the grounds that the app never asks for the location permission — AdMob derives coarse location from IP.
- Transfers to a *"service provider"* are **not** "sharing". **AdMob/Google is not a service provider** in this sense — it is the recipient of the data for its own advertising purposes. Mark these rows **shared**, not just collected.

Also declare **data encrypted in transit = Yes** (Google states all SDK data is TLS-encrypted) and mark the ad ID as **resettable** (Play's "Is this data ephemeral / can users request deletion" answers follow from the OS-level ad-ID reset).

If you later add analytics (Firebase, GA4), those are additional rows — out of scope here.

### 3.3 UMP consent requirements (EEA / UK / Switzerland — note: Switzerland too)

- Scope is broader than the card assumed: Google's UMP article targets *"eligible **EEA (European Economic Area), UK (United Kingdom), and Swiss** users"*. Treat Switzerland as in-scope.
- UMP is not optional decoration: *"Limited ads serving applies if there is no consent for IAB TCF Purpose 1 in accordance with the EU user consent policy."* No consent → dramatically lower (or zero) revenue from EU traffic.
- The UMP SDK is *"IAB-certified for TCF v2.3 across all SDK versions"* — the plugin ships UMP 3.1.0, so this is covered.
- **You must create the message in AdMob, or the SDK has nothing to show.** AdMob → Privacy & messaging → European regulations → Create message → select the app → languages → "Do not consent" option → Targeting: **Countries subject to GDPR (EEA, UK, and Switzerland)** → add a privacy policy URL → **Publish**. A saved draft does nothing.
- Required code order (plugin README, matches Google's guidance): `initialize()` → `requestConsentInfo()` → `showConsentForm()` only if `canRequestAds === false` → only then `showBanner()`.
- Give users a way back in: call `AdMob.showPrivacyOptionsForm()` from a settings/privacy link. `privacyOptionsRequirementStatus` tells you whether to show that link.
- AdMob also has a separate **US state privacy law** message type (CPRA/CPA etc.). Create and publish it in the same Privacy & messaging screen. Not legally identical to GDPR, but same tooling.

### 3.4 Spirituality content + ad placement — where this app can get flagged

Four real risks for a tarot app with a top banner:

1. **Ad maturity vs app rating.** Play: *"any ads that appear in the app must not be significantly more mature in content than the primary content within the app itself."* A tarot app will be rated Everyone/Teen; a dating-or-gambling ad rendered inside it is a violation **by the app**, not by the advertiser. Mitigate in AdMob: app-level **max ad content rating = Teen or General**, plus a **blocklist** for categories you don't want (dating, gambling, weight loss, politics). Do this *before* launch, not after a violation.
2. **"Ads should not float or hover over app content."** This is the one that directly threatens the planned design. Google's *Discouraged banner implementations* page classifies *"Ad overlapping with app content"* as **"against policy"**: *"Banner ads should not float or hover over app content ... banner ads should not move as a user scrolls."* Because the Capacitor banner is a **native view painted on top of the WebView**, a naive implementation is literally an ad hovering over content. The inset fix from §2.2 is a **policy requirement**, not a polish item. **Status 2026-10-27:** implemented as a fixed, non-scrolling app shell that starts at the banner's bottom edge (`html{overflow:hidden}` + `body{position:fixed; top:var(--admob-banner-inset); bottom:0; overflow-y:auto}`), so scrolled content cannot pass behind the ad (QA defect DEF-2, card `t_e4430cd7`). A `padding-top` spacer alone is **not** sufficient — it sits inside the scroll container and still lets content scroll under the banner. The CSS block is commented so a future refactor doesn't remove it.
3. **Accidental clicks from proximity to interactive UI.** Google: banner ads *"should not be placed next to interactive buttons"* (next button, menu bar) or on screens where users continuously tap. Tarot apps are tap-heavy: card selection, "draw" buttons. A top banner sitting directly above a row of card-spread buttons is exactly the pattern that generates invalid clicks. Leave dead space (or a non-interactive header) between banner and any tappable element, and never put the banner adjacent to the "Draw" button.
4. **Invalid traffic / self-clicks.** Google: *"Publishers may not click their own ads ... Testing your own ads by clicking on them is not allowed."* Consequences are account-level and permanent (*"if a publisher had an AdMob account closed for invalid activity ... they would not be able to use AdSense to monetize"*, and opening another account gets both closed). **Always use `isTesting: true` or registered test devices while developing.** This is the single easiest way to lose the business asset.

Banner ads are otherwise explicitly fine under Play's "Better Ads Experiences" rules — those rules cover full-screen interstitials and state *"This policy ... does not apply to ... non-full screen banner ads."* So a passive top banner is the lowest-risk ad format available to this app.

### 3.5 ads.txt / app-ads.txt — not needed for this build

**One line: not required.** `app-ads.txt` is an ownership-verification mechanism for programmatic inventory; AdMob only crawls for it *"based on the developer website in your app's store listing"*, which this app does not have. It has no effect on whether a native banner serves. Revisit only if you later list a developer website and want to block domain-spoofed inventory.

### 3.6 AdMob account → app → ad unit flow (James, with his Google login)

1. https://admob.google.com → sign in with the account that owns Play Console → accept terms (payment/tax info needed).
2. **Apps → Add app → Android** → "Is your app listed on a supported app store?" → Yes → search `Decision Tarot` (or paste `com.keystonedigital.decisiontarot`). If Play hasn't indexed it, choose "No" and add the app manually with the package name — AdMob will match it later.
3. Copy the **App ID** (`ca-app-pub-…~……`) — note `~`.
4. **Ad units → Add ad unit → Banner** → name `decisiontarot_banner_top` → Create → copy the **ad unit ID** (`ca-app-pub-…/……`) — note `/`.
5. **Privacy & messaging → European regulations** → create + **Publish** (§3.3). Then **US state privacy law** message.
6. **Blocking controls** → set **max ad content rating** to Teen/General; add category blocklist (§3.4).
7. **Payments** → verify identity + tax → this is where revenue lands (and where the $100 threshold applies, §5.3).
8. Optional: **Apps → App settings → link to Play Store** so AdMob can show store metrics.

You will end up with exactly two strings that go into the app: the **App ID** (into `strings.xml`) and the **banner ad unit ID** (into the web layer). Nothing else from AdMob needs to be in the repo, and no secret keys are involved — AdMob IDs are public identifiers.

---

## 4. Pricing scan — 7 comparable apps

Method: every figure below was read directly from the app's Google Play listing on 2026-10-27
(`play.google.com/store/apps/details?id=<pkg>&hl=en&gl=US`), not from blog posts. Google Play
publishes in-app purchase **ranges**, not the individual SKU prices — the range is the honest,
verifiable number and is what's tabulated. Individual price points were only recorded where an
authoritative page stated them.

| App | Package | Installs | Price model from listing | "Contains ads" |
|---|---|---|---|---|
| **Labyrinthos Tarot** | `com.labyrinthos.app` | 1,000,000+ | Free + IAP **$0.99 – $89.99** per item | No |
| **Co-Star Astrology** | `com.costarastrology` | 5,000,000+ | Free + IAP **$0.99 – $74.99** per item | No |
| **Galaxy Tarot** | `com.hemisphere3.tarot` | 1,000,000+ | Free + IAP **$0.99 – $14.99** per item | **Yes** |
| **Trusted Tarot** | `com.trustedtarot.app` | 100,000+ | Free + IAP **$1.99 – $84.99** per item | **Yes** |
| **Tarot Simple** | `com.myapp.tarotsimple` | 1,000+ | Free + IAP **$0.99 – $59.99** per item | **Yes** |
| **Psychic Tarot Oracle** | `com.oceanhouse_media.ocardspsychictarot` | not captured | **Paid app $9.99 one-time**, no IAP | **Yes** |
| **Golden Thread Tarot** | `co.threadweave.goldenthread` | not captured | Free, no IAP detected | No |

Verified individual price points:

- **Labyrinthos Premium: $9.99/month, $24.99/quarter, $89.99/year** — the market leader's public
  pricing. It also sells readings à la carte via credits (~$0.10/reading) so a light user can pay
  far less than a subscription. ([tarotalmanac comparison](https://www.tarotalmanac.com/vs/labyrinthos), [App Store price tracker](https://appstoreprice.org/en/apps/1155180220))
- **Psychic Tarot Oracle: $9.99 flat, one-time, and it still shows ads.** Read from the Play page's
  structured data (`"price": "9.99"`, `"priceCurrency": "USD"`). This is the closest single data
  point to "what a casual tarot user will actually pay once".
- **Galaxy Tarot's entire IAP catalog tops out at $14.99.** For a 1M+ install app that's the
  strongest signal in the table: the mass-market tarot ceiling for one-off purchases is *low*.

### 4.1 Is $9.99/mo + $79.99/yr priced out of market?

**No — it is priced *at* the market leader's rate, which is a different problem.** James's
$9.99/mo matches Labyrinthos Premium exactly, and $79.99/yr undercuts Labyrinthos's $89.99/yr.
On paper the subs are correctly positioned. The honest read of why there are zero sales is not
the price of the subscription — it is:

1. **No traffic.** Single-digit installs/day means a sub at *any* price converts to zero. At a
   typical 1–2% subscription conversion, 5 installs/day ≈ 0–1 sale per *month*, and only after a
   few months of accumulated installs.
2. **No low-commitment option.** Every high-install competitor lets a curious user spend
   $0.99–$14.99 once. This app asks $9.99 up front with no trial, no cheap unlock, and no free
   path that is monetized — so a curious user either pays the highest-in-market monthly rate or
   leaves and costs nothing. Adding ads without adding a small one-time purchase leaves that gap
   wide open.
3. **The premium pitch is thin for a local app.** Multi-card spreads in a fully offline app is a
   small feature delta versus Labyrinthos's content library, brand and lessons. Charging the
   same monthly rate as a content-rich, cloud-synced competitor is defensible only on "we're
   cheaper yearly", which nobody discovers at zero traffic.

### 4.2 Recommendation: YES — add one lifetime SKU

Add a single non-consumable that **removes ads + unlocks all spreads**, and keep both existing
subs. Reasons:

- It converts the traffic you *do* get. A $19.99 one-time at 1% of 5 installs/day is still
  small — but it is a real number, where $9.99/mo converts near zero.
- Evidence says the ceiling is real but low: Galaxy Tarot's catalog tops at $14.99; the
  best-selling paid tarot app is $9.99. **Anything above ~$25 will not convert for a casual
  tarot app regardless of how good the spreads are.**
- It is the natural pairing with ads: "remove ads forever" is a purchase users understand
  instantly, and it gives the banner a *job* beyond annoyance.
- Keeping the subs preserves the higher-LTV path for the rare committed user, and Google Play
  supports all three SKUs side by side with no conflict.

### 4.3 Recommended SKUs

| SKU | Type | Price | Grants |
|---|---|---|---|
| `tarot_monthly` (**exists**) | auto-renewing sub | $9.99/mo | no ads + all spreads |
| `tarot_yearly` (**exists**) | auto-renewing sub | $79.99/yr | no ads + all spreads |
| **`tarot_lifetime`** (new) | **one-time product (INAPP), non-consumable** | **$19.99** | no ads + all spreads, forever |

Notes and a correction:

- **Product-ID correction:** the card says the yearly SKU is `yearly_base`, but the shipped code
  and the repo's own billing docs both use **`tarot_yearly`** (`public/index.html` line ~1273,
  `PLAY_STORE_BILLING_STATUS.md`, `GOOGLE_PLAY_BILLING.md`). Whatever is in Play Console must
  match `tarot_yearly` or the yearly purchase fails silently. Verify this in Play Console →
  Monetize → Products → Subscriptions before touching anything else — it's a 30-second check and
  a candidate explanation for part of the zero-sales picture.
- Naming `tarot_lifetime` matches the existing `tarot_monthly` / `tarot_yearly` convention.
- **$19.99 is the recommendation; $14.99 is a defensible launch price.** Avoid $9.99 — it equals
  one month of the sub and makes the sub look absurd. Avoid ≥$24.99 — above the Galaxy Tarot
  ceiling.
- Play-side setup: **Monetize → Products → In-app products → Create product**, ID
  `tarot_lifetime`, name "Tarot Lifetime", price $19.99, then **Activate**. (Subscriptions live
  in a different screen — Products → Subscriptions — and one-time products cannot be created
  from there.)

### 4.4 Code implication of the lifetime SKU (flag for whoever implements it)

The billing plugin **hardcodes `BillingClient.ProductType.SUBS` in all four paths**:
`queryProducts` (line ~160), `purchase` (line ~227), `isPremium` (line ~284),
`restorePurchases` (line ~311) — verified in
`android/app/src/main/java/com/keystonedigital/decisiontarot/plugins/billing/GooglePlayBillingPlugin.java`.

A one-time product is `ProductType.INAPP`, **not** `SUBS`. So `tarot_lifetime` will not appear in
`queryProducts` and will not be returned by `isPremium()` until the plugin queries **both**
product types and treats either as premium. Purchases are acknowledged already (line ~131), so
there is no 3-day auto-refund risk. This is ~30 lines of Java plus a third paywall card in
`index.html` — small, but not zero, and it must ship in the *same* release as the Play Console
product or the button will error.

---

## 5. Revenue benchmarks — what a banner will actually earn here

### 5.1 Banner eCPM ranges (2025–2026)

| Source | Banner eCPM |
|---|---|
| [AppAdRevenue benchmarks guide](https://www.appadrevenue.com/guides/how-much-do-mobile-ads-pay) (updated July 2026, blended 2024–2025) | **$0.07** (Tier-3 low) → **$1.00** (Tier-1 high) |
| [Playwire AdMob eCPM benchmarks](https://www.playwire.com/blog/admob-ecpm-benchmarks-what-publishers-should-expect) — US/UK/CA/AU/DE/FR/JP | **$0.50 – $1.50** |
| Same, global average across all geos | **$0.20 – $0.80** |

Both sources independently label banners "volume filler" / "sounds pathetic until you remember
banners generate dozens of impressions per session." For a US-heavy spirituality app, plan on
**$0.20–$0.80 eCPM realistic, $1.00 optimistic**.

There is no credible public data source that breaks eCPM out by "spirituality" specifically; the
entertainment/lifestyle ranges above are the closest honest anchor. Treat any number claiming
$3+ banner eCPMs as either rewarded-format or affiliate marketing.

### 5.2 The math for single-digit-installs-per-day traffic

Assume 2 banner impressions per DAU per day (a draw-a-card session, maybe twice) and a blended
**$0.50 eCPM** — generous for mixed-geo traffic.

| DAU | Impressions/day | Revenue/day | Revenue/month | One `tarot_lifetime` sale ($19.99, ~$16.99 net after Play's 15%) |
|---|---|---|---|---|
| 50 | 100 | $0.05 | **~$1.50** | ≈ **11 months** of ads |
| 100 | 200 | $0.10 | **~$3.00** | ≈ 5.7 months of ads |
| 500 | 1,000 | $0.50 | **~$15** | ≈ 1.1 months of ads |
| 2,000 | 4,000 | $2.00 | **~$60** | ≈ 0.3 months of ads |

Read the first two rows again: **a single $19.99 lifetime purchase is worth roughly half a year
to a year of banner revenue at this app's realistic traffic.** That is the whole argument for
§4.3 in one line — ads are not the business, they are the nudge that makes the $19.99 purchase
worth making.

### 5.3 The payout threshold

AdMob pays out only after the account balance crosses **$100** (standard AdSense/AdMob threshold),
on a monthly cycle, after payment verification. At 50–100 DAU earning $1.50–$3.00/month, that
threshold is **3–5 years away**. Practically: treat ad revenue as $0 for planning purposes until
traffic grows by an order of magnitude. The banner's real job is conversion pressure, not income.

Two more honest caveats:

- **Invalid-traffic deductions are retroactive.** AdMob estimates earnings, then finalizes them;
  low-quality traffic (and a brand-new publisher with a new app) gets deductions. Early numbers
  overstate.
- **Banner on the free tier can suppress the subscription.** A permanent ad bar in a
  contemplative, full-screen-feel tarot UI is more intrusive than in a game. Keep it
  `ADAPTIVE_BANNER` at the top, never full-width-tall, never animated, and never on the card
  reading screen mid-spread — otherwise the "remove ads" pitch becomes "this app got worse."

### 5.4 Strategic verdict (Dave's recommendation)

1. Ship the banner — it's cheap, policy-compliant as designed, and it makes the paid tier
   meaningful.
2. Add `tarot_lifetime` at $19.99 in the same release. **This is the actual revenue change**, not
   AdMob.
3. Do **not** expect AdMob to be a revenue line in 2026. Expect <$5/month until DAU is in the
   hundreds.
4. The only lever that changes any of these numbers is installs. Monetization work on an app with
   single-digit installs/day is optimizing the wrong variable — but doing it now is still right,
   because it costs a day and removes a blocker for when traffic does arrive.

---

## 6. Sources

Plugin / build (npm registry + published package contents, 2026-10-27):
- https://registry.npmjs.org/@capacitor-community/admob (version map; 8.2.1 latest, 7.2.0 last Cap-7 stable)
- https://registry.npmjs.org/admob-plus (2.0.0-alpha.18, 2024-11-15)
- https://registry.npmjs.org/@capacitor/core (7.6.9 = latest-7 line; repo is on 7.6.5)
- `@capacitor-community/admob@7.2.0` tarball: `dist/esm/**/*.d.ts`, `android/build.gradle`, `README.md`

Google policy documentation:
- Play, Ads policy — https://support.google.com/googleplay/android-developer/answer/9857753
- Play, app content / "Contains ads" declaration — https://support.google.com/googleplay/android-developer/answer/9859455
- Play, Data safety — https://support.google.com/googleplay/android-developer/answer/10787469
- AdMob, Google Play data disclosure (SDK data categories) — https://developers.google.com/admob/android/privacy/play-data-disclosure
- Play, Content ratings (IARC) — https://support.google.com/googleplay/android-developer/answer/9898843
- AdMob, European regulations message (UMP) — https://support.google.com/admob/answer/10113207
- AdMob, Banner ad guidance — https://support.google.com/admob/answer/6128877
- AdMob, Discouraged banner implementations (overlap = against policy) — https://support.google.com/admob/answer/6275345
- AdMob, Behavioral policies (invalid clicks, framing) — https://support.google.com/admob/answer/2753860
- AdMob, app-ads.txt setup — https://support.google.com/admob/answer/9363762
- AdMob, Invalid traffic — https://support.google.com/admob/answer/3342054

Market / revenue:
- AppAdRevenue, "How Much Do Mobile Ads Actually Pay?" — https://www.appadrevenue.com/guides/how-much-do-mobile-ads-pay
- Playwire, "AdMob eCPM Benchmarks" — https://www.playwire.com/blog/admob-ecpm-benchmarks-what-publishers-should-expect
- Labyrinthos pricing — https://www.tarotalmanac.com/vs/labyrinthos , https://appstoreprice.org/en/apps/1155180220
- Play listings (fetched 2026-10-27, `hl=en&gl=US`): `com.labyrinthos.app`, `com.costarastrology`, `com.hemisphere3.tarot`, `com.trustedtarot.app`, `com.myapp.tarotsimple`, `com.oceanhouse_media.ocardspsychictarot`, `co.threadweave.goldenthread`

Repo evidence (read-only):
- `android/variables.gradle` (minSdk 23, compileSdk/targetSdk 36), `android/build.gradle` (AGP 8.3.0, Kotlin 1.9.0), `android/gradle/wrapper/gradle-wrapper.properties` (Gradle 8.4)
- `android/app/build.gradle` (versionCode 62, google-services applied only if `google-services.json` exists — it does)
- `public/index.html` (~line 1266: `tarot_monthly`, `tarot_yearly`; `isPremium` flag via `localStorage.premium_unlocked`)
- `android/app/src/main/java/.../plugins/billing/GooglePlayBillingPlugin.java` (all queries `ProductType.SUBS`; acknowledges at ~line 131)

---

## 7. What I did NOT verify (be suspicious of these)

- **No AdMob UI walked as James.** The account/app/ad-unit flow in §3.6 is from Google's docs, not
  from clicking through with his login. Screen names may differ slightly.
- **Play Store IAP ranges ≠ individual SKU prices.** The ranges are what Google publishes publicly.
  What Labyrinthos actually charges for which tier is only verifiable from inside its paywall.
- **Co-Star pricing not verified** — searches returned the unrelated commercial-real-estate CoStar.
  Only its listing data (IAP $0.99–$74.99, no ads) is used.
- **Installs for Psychic Tarot Oracle and Golden Thread Tarot were not reliably captured** (the
  pages embed unrelated numbers). Left blank rather than guessed.
- **No spirituality-specific eCPM source exists publicly.** §5.1 uses entertainment/lifestyle
  benchmarks as the closest honest proxy.
- **No build was attempted.** The toolchain mismatch in §2.1 is read from the two `build.gradle`
  files; whether it actually breaks is unproven until someone runs `./gradlew assembleDebug`.
