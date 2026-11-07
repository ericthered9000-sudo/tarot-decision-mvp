# DEF-1 / DEF-2 banner fixes — build & measurement record

**Card:** `t_e4430cd7` (Bob) · **Parent QA:** `t_1e8f4279` · **Date:** 2026-10-27
**Build under test:** debug APK, `versionCode 63` / `versionName 5.1.0`, `assembleDebug` exit 0

| Artifact | md5 |
|---|---|
| `public/index.html` | `679fd90faa8ab6ef76cfb84b45d72719` |
| `android/app/src/main/assets/public/index.html` | `679fd90faa8ab6ef76cfb84b45d72719` |
| `assets/public/index.html` **inside** `app-debug.apk` | `679fd90faa8ab6ef76cfb84b45d72719` |
| `app-debug.apk` (12,717,303 B) | `7a2b4650e36f5fe45d1ba6fddb89cafb` |

`npx cap sync android` → zero drift (all three copies share the md5 above).
Device: `emulator-5554` (sdk_gphone64_x86_64, API 36, dpr 2.625, viewport 411×838 CSS).

---

## DEF-1 — inset double-conversion — FIXED

`applyBannerInset()` no longer multiplies the plugin's dp height by `devicePixelRatio`:

```js
const px = Math.max(0, Math.round(heightDp || 0)); // dp -> CSS px is 1:1
```

`admobDpr()` is still used by `statusBarHeightDp()`, which converts CSS px → dp for the plugin's
`margin` option (the opposite direction, still correct).

| Measurement (free tier, banner loaded) | Before (v63) | After |
|---|---|---|
| logcat `[AdMob] content inset =` | `168px (banner 64dp)` | **`64px (banner 64dp)`** |
| `--admob-banner-inset` | 168px | **64px** |
| shell offset / `padding-top` | `padding-top: 188px` | **`top: 64px` + `padding: 20px`** |
| `h1` top, viewport-relative | 188 CSS px | **84 CSS px** |
| dead band under the ad | ~104 CSS px | **0** |

Native confirmation (`uiautomator dump`): AdView `[0,136][1078,304]` = 168 physical px = 64 CSS px,
identical to the reserved band.

## DEF-2 — content scrolled beneath the fixed banner — HARDENED

The app shell is now a fixed, non-scrolling container that begins at the banner's bottom edge and
scrolls internally:

```css
html { height: 100%; overflow: hidden; }
body {
  position: fixed;
  top: var(--admob-banner-inset, 0px);
  left: 0; right: 0; bottom: 0;
  overflow-y: auto; overflow-x: hidden;
  padding: 20px;
}
```

Evidence (free tier, banner loaded, scrolled to the end of the page):

* CDP: `body` `position: fixed`, `rectTop = 64` **before and after scrolling**; `window.scrollY = 0`
  and `documentElement.scrollTop = 0` at all times — the document can no longer scroll.
* CDP: `body.scrollTop` 0 → 451.4 (max), `clientHeight 774` = 838 − 64, `scrollHeight 1226`.
* CDP hit-test in the banner band while scrolled: `y=2,20,40,62 → HTML` (nothing but the fixed
  starfield/canvas), `y=65,70,80 → BODY`. `contentPaintsInBand = []`.
* `uiautomator`: the scrollable view is `[0,304][1080,2337]` — its top is **exactly the AdView's
  bottom edge** (304 physical px = 64 CSS px below the WebView top). There is no scroll container
  starting at the WebView top any more.
* Screenshots: `d9_free_idle_final.png` (at rest, no dead band), `d10_free_midscroll_final.png`
  (clean clip at the banner's lower edge).

Docs corrected so the bad formula cannot regress: `docs/admob-research.md` §2.2 (formula + the
"reserve the band, don't pad the scroller" rule), §1.2 (integration outline) and §3.4 risk 2
(status + why a padding spacer alone is insufficient).

## Regression checks (same build)

| Check | Result |
|---|---|
| Tabs reading / daily / encyclopedia / history / dashboard | all `active`, render 10–456 nodes, 209–7816 chars, pane top 318 CSS px, shell scrolls independently |
| Free 1-card draw | ok, 1 card, 791 chars, no paywall (metering untouched) |
| `showPaywall()` overlay | viewport-anchored `top 0 → bottom 838` (= innerHeight), 2 tiers: `$9.99 one-time` + `$4.99 per month`, no yearly |
| Premium (cold start, `premium_unlocked`) | `isPremium true`, `--admob-banner-inset` unset, `body.top 0`, `h1` top **20 CSS px**, logcat `premium user — banner never requested`, `banner requested` count **0**, zero ad nodes in `uiautomator` |
| `npx cap sync android` drift | zero (md5 identical, see table) |
| `./gradlew assembleDebug` | exit 0 |

## AI path (step 6)

`GROQ_API_KEY` is **still unset** in Vercel Production — verified after this build:

```
POST https://tarot-decision-demo.vercel.app/api/reading → HTTP 200 in 0.27 s
{"ai_generated":false,"message":"AI reading unavailable — using template fallback. Set GROQ_API_KEY env var.", ...}
```

Not blocking this card. `ai_generated:true` + in-app AI-written premium text remain unverified
until James adds the key and redeploys.

## Residual observations (not introduced by this change; no action taken)

1. **Full-screen modals still paint in the banner band.** Paywall / card-detail overlays are
   `position: fixed` viewport overlays (`top: 0 → bottom: 838`), so the native banner covers the
   top 64 px of their *dimmed backdrop*. Their content is vertically centred and never hidden, but
   if we want to be strict about "no app UI behind the ad", inset `#paywall-modal` and
   `.modal-overlay` by `var(--admob-banner-inset)` too. Left alone on purpose: it changes verified
   monetization UI, so it should be a separate decision.
2. **Soft keyboard overlays the WebView (Android 16 edge-to-edge).** With the keyboard open,
   `innerHeight` stays 838 and `visualViewport.height` stays 838 — the IME is not inset, so a field
   tapped low on the screen can be hidden behind the keyboard (the user can scroll it back). A/B
   tested in this same build by restoring the old body CSS at runtime: identical behaviour, so this
   is **pre-existing**, not a DEF-2 side effect. Real fix = handle IME insets in `MainActivity` or
   set `windowSoftInputMode`, which is outside this card.
3. **Reload while a banner is live leaves the native AdView up** with no JS inset until the new boot
   re-requests the banner (page reload does not call `removeBanner()`). The real upgrade path calls
   `removeTopBanner()` before reloading, and premium cold start requests nothing, so this only shows
   up when toggling premium via `localStorage` + reload (a dev-only path).
4. **`versionCode` was deliberately not bumped** (still 63 / 5.1.0). Bump it when the signed AAB is
   rebuilt for upload so the Play track and QA builds stay distinguishable.

## How to re-verify

```
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep "AdMob] content inset"      # expect 64px (banner 64dp)
# CDP: body {position:fixed, top:64}, h1 top 84, window.scrollY 0 while body.scrollTop changes
adb shell uiautomator dump && adb shell cat /sdcard/window_dump.xml | grep -o 'bounds="\[0,304\]'
```
