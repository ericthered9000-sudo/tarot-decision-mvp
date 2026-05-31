# Decision Tarot — QA Test Plan

**App:** Decision Tarot (com.keystonedigital.tarot)
**Version:** 4.5 (build 50)
**Platform:** Android (Capacitor 5.7.8 / WebView)
**API Dependencies:** tarotapi.dev (cards data) + /api/reading (AI interpretations)
**Monetization:** RevenueCat Capacitor v6.1.2 → Google Play Billing
**Known Issues to Verify:** GA4 (G-DKXVEZH3DL) must be removed; minSdkVersion must be bumped from 22→23; RevenueCat v6.1.2 must be upgraded to v13.x

---

## 1️⃣ Install & Launch

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 1.1 | Install APK on device (Android 13+ — API 33) | APK installs without error; app icon "Tarot Decision" appears in launcher | □ | □ |
| 1.2 | Install APK on lowest supported device (API 22 / Android 5.1) — note: should be bumped to API 23 | Installs successfully (if minSdk 22) or shows "App not installed" (if minSdk ≥ 23 and device too old) | □ | □ |
| 1.3 | Cold launch from launcher | Splash screen appears (core-splashscreen lib), transitions to main WebView | □ | □ |
| 1.4 | First-launch experience | Starfield background animates, "Decision Tarot — Clarity at Life's Crossroads" title displays, 5 navigation tabs visible (Draw, Daily Card, Cards, Past, Dashboard) | □ | □ |
| 1.5 | Google Fonts render correctly | Cinzel used for headings, Quicksand for body text — both loaded from Google Fonts CDN | □ | □ |
| 1.6 | GA4 tag presence check (compliance) | index.html lines 12–22 contain `<script>` for G-DKXVEZH3DL — must NOT be present per compliance docs | □ | □ |
| 1.7 | Check for console errors at launch | No JS errors in WebView console; `initRevenueCat` logs success/failure gracefully | □ | □ |
| 1.8 | Relaunch after force-close | App resumes cleanly without crash; localStorage state intact | □ | □ |

---

## 2️⃣ Free Tier (1-Free-Reading-per-Day Gate)

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 2.1 | Fresh install — 1st card draw | Draw button works; cards animate (flip); reading displays; no paywall shown | □ | □ |
| 2.2 | 2nd card draw on same day (free user) | Paywall modal appears immediately on clicking "Draw Your Cards" — user cannot draw | □ | □ |
| 2.3 | Daily Card tab — 1st view on same day | Daily Card loads and displays without paywall (daily card uses same dailyDraws counter per `getDailyDrawCount()`) | □ | □ |
| 2.4 | Daily Card tab — 2nd attempt same day | Paywall triggers in Daily Card tab: "Daily card limit reached. Upgrade to unlimited." with unlock button | □ | □ |
| 2.5 | localStorage dailyDraws counter reset | Advance device date to next day → verify `getDailyDrawCount()` returns 0 and draw works again | □ | □ |
| 2.6 | localStorage key integrity | Verify `dailyDraws` stored as `{"date":"...", "count":1}`; verify `dailyCard` stored separately; no data corruption | □ | □ |
| 2.7 | Category selector for free draw | All 6 categories (Career, Relationship, Life Path, Money, Health, Other/Custom) selectable; validation requires selection | □ | □ |
| 2.8 | Custom question text area | Selecting "Other/Custom" reveals textarea; entering question works; empty textarea shows error | □ | □ |
| 2.9 | Optional name field | Name input shows in reading header ("James's reading about...") | □ | □ |

---

## 3️⃣ Paywall

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 3.1 | Paywall triggers on 2nd draw | Modal overlay appears with dimmed background, z-index 1000, paywall-tier cards visible | □ | □ |
| 3.2 | All 3 pricing tiers displayed | Lifetime ($9.99 one-time), Pro+ Monthly ($9.99/mo), Pro+ Yearly ($79.99/yr) — all present with correct prices | □ | □ |
| 3.3 | "Best Value" badge on yearly | Yearly tier has gold "BEST VALUE" badge at top-right | □ | □ |
| 3.4 | Tier selection visual feedback | Clicking a tier highlights it with gold box-shadow; other tiers reset to default border | □ | □ |
| 3.5 | CTA button text updates per tier | Button shows "Get Lifetime Access — $9.99", "Get Pro+ Monthly — $9.99/mo", "Get Pro+ Yearly — $79.99/yr" depending on selection | □ | □ |
| 3.6 | Close / "Maybe Later" button | Clicking "Maybe Later" or tapping overlay background dismisses modal | □ | □ |
| 3.7 | Paywall re-trigger after dismiss | If user closes paywall without purchase and tries to draw again, paywall reappears | □ | □ |
| 3.8 | Purchase flow — tap CTA | "Processing..." text appears, button disabled; RevenueCat purchase flow initiated; Google Play Billing dialog appears | □ | □ |
| 3.9 | Purchase cancellation | User cancels in Google Play dialog → no charge, paywall stays, button re-enables | □ | □ |
| 3.10 | Purchase success | Modal closes, "🎉 Thank you! Your premium features are now unlocked." alert shown; `localStorage.premium_unlocked` = 'true' | □ | □ |
| 3.11 | Paywall footer text | "Secure via Google Play Billing • 15% fee applies • Cancel anytime" visible | □ | □ |
| 3.12 | Paywall from Daily Card tab | Same paywall modal shows when tapping "Unlock Unlimited" on daily card limit | □ | □ |

---

## 4️⃣ Readings (1-Card, 3-Card, Celtic Cross)

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 4.1 | 1-card spread selection | "Single Card" button selectable; spread info updates to "Your Guidance" position | □ | □ |
| 4.2 | 3-card spread selection | Default active; positions: The Situation, The Choice, The Outcome | □ | □ |
| 4.3 | Celtic Cross (7-card) selection | 7 positions displayed in 3-3-1 row layout; cards smaller for mobile fit (60×100px) | □ | □ |
| 4.4 | Card flip animation | Cards start as face-down (CSS harlequin diamond back); stagger-reveal at 600ms intervals per card | □ | □ |
| 4.5 | Animation CSS | `mysticalReveal` keyframes: rotateY(90deg) → rotateY(0deg), blur → sharp, scale 0.8 → 1.0, 0.8s duration | □ | □ |
| 4.6 | Card images display | Image loads from `cards/{filename}` using `getImageFilename()` mapping; fallback SVG on error | □ | □ |
| 4.7 | Reversed cards shown | Cards randomly assigned upright/reversed (25% reversal for 1/3-card, 35% for Celtic Cross); label shows "Reversed" / "Upright" | □ | □ |
| 4.8 | Card meanings display | Each card shows name, position label, meaning_up or meaning_rev text from tarotapi.dev | □ | □ |
| 4.9 | Reading synthesis section | Summary shows energy assessment (upright count analysis) and Major vs Minor arcana insight | □ | □ |
| 4.10 | AI-powered reading endpoint | POST to `/api/reading` returns structured data; if fails, falls back to template `getReading()` | □ | □ |
| 4.11 | "AI-powered reading" badge | If `data.ai_generated` is true, badge text "✨ AI-powered reading" shown | □ | □ |
| 4.12 | Crystal ball loading animation | While fetching, crystal ball pulse animation and "Consulting the cards..." text appear | □ | □ |
| 4.13 | Tap card for detail | Clicking a card → card modal overlay; shows larger image, upright meaning, reversed meaning, keywords | □ | □ |
| 4.14 | Card detail modal close | Click × or overlay background closes modal | □ | □ |
| 4.15 | "Start New Reading" button | Resets reading area, returns to question form, clears selections | □ | □ |
| 4.16 | Error state | If `/cards/random?n=N` fails or `/api/reading` fails → error message displayed, form re-shown | □ | □ |

---

## 5️⃣ Encyclopedia

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 5.1 | Encyclopedia tab loads all 78 cards | Grid of card thumbnails (60×100px) displays; loaded from tarotapi.dev `/cards` endpoint | □ | □ |
| 5.2 | All 22 Major Arcana present | Fool through World (ar00–ar21) all appear with correct names | □ | □ |
| 5.3 | All 56 Minor Arcana present | Cups, Wands, Swords, Pentacles — Ace through King for each suit | □ | □ |
| 5.4 | Card thumbnail images | Each card shows JPG from `cards/` directory; error fallback renders inline SVG placeholder | □ | □ |
| 5.5 | Search bar filters cards | Typing in search input filters by name OR upright meaning; results update in real-time | □ | □ |
| 5.6 | Tap card → detail modal | Full card detail modal opens with upright meaning, reversed meaning, keywords | □ | □ |
| 5.7 | Encyclopedia preloads on init | `initApp()` fetches all cards on page load; `displayEncyclopediaCards()` called only from cache on tab switch | □ | □ |
| 5.8 | Empty search results | Search term with no matches → grid empty, no error | □ | □ |

---

## 6️⃣ History

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 6.1 | Reading auto-saved to history | After draw completes, `saveReading()` stores in `localStorage.readingHistory` array | □ | □ |
| 6.2 | History tab shows past readings | Tab loads from localStorage; displays date, spread name, question, card names (with ↺ for reversed) | □ | □ |
| 6.3 | Empty history state | Fresh install → "No readings yet" message with book icon | □ | □ |
| 6.4 | "View Reading" from history | Click → switches to reading tab, shows past reading cards and interpretation | □ | □ |
| 6.5 | "Track Outcome" button | Opens outcome modal with Good/Neutral/Bad selector + notes textarea | □ | □ |
| 6.6 | Save outcome | Outcome stored in history entry; emoji indicator shown in history list (👍/➖/👎) | □ | □ |
| 6.7 | Outcome tracking paywall-gated | Free users get tracking paywall instead of outcome form | □ | □ |
| 6.8 | "Clear History" button | Confirmation dialog → on confirm, `localStorage.readingHistory` removed, list shows empty state | □ | □ |
| 6.9 | History limit (50 entries) | After 50 readings, oldest entry is evicted (pop) | □ | □ |
| 6.10 | Update outcome on existing entry | "Update Outcome" button replaces outcome; UI refreshes | □ | □ |

---

## 7️⃣ Offline Behavior

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 7.1 | Launch with no internet | App launches; starfield animates; UI renders; no crash | □ | □ |
| 7.2 | Attempt card draw offline | Fetch to tarotapi.dev fails → retry logic triggers (2 retries at 1s backoff); eventually shows "Unable to connect to server" error | □ | □ |
| 7.3 | Attempt AI reading offline | POST to `/api/reading` fails → fallback to `getReading()` template; `fetchWithRetry` eventually errors | □ | □ |
| 7.4 | Encyclopedia offline | `initApp()` fails → allCards stays empty; Encyclopedia tab shows "Unable to load cards" message | □ | □ |
| 7.5 | View history offline | History loads from localStorage — works without internet (local data only) | □ | □ |
| 7.6 | View already-rendered reading offline | Reading area uses in-memory DOM — previous reading stays visible if already rendered | □ | □ |
| 7.7 | Card images offline | Images at `cards/{filename}` are bundled in APK assets → display correctly without internet | □ | □ |
| 7.8 | Paywall interactions offline | Paywall UI renders; purchase click → RevenueCat plugin errors (no network) → error alert shown gracefully | □ | □ |
| 7.9 | Restore purchases offline | RevenueCat API call fails → falls back to `localStorage.getItem('premium_unlocked')` | □ | □ |
| 7.10 | Daily card offline | Cached daily card (localStorage) shown if cached; otherwise fetch fails with error message | □ | □ |

---

## 8️⃣ Premium Unlock

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 8.1 | Purchase lifetime access | Google Play dialog; on success, `localStorage.premium_unlocked` = 'true', `premium_tier` = 'lifetime' | □ | □ |
| 8.2 | Purchase monthly Pro+ | Success: `premium_tier` = 'pro_plus', premium_unlocked set | □ | □ |
| 8.3 | Purchase yearly Pro+ | Success: `premium_tier` = 'pro_plus', premium_unlocked set; lifetime entitlement also grants pro_plus per code comment | □ | □ |
| 8.4 | After purchase — daily gate removed | 2nd, 3rd, Nth draw all work without paywall; unlimited draws | □ | □ |
| 8.5 | After purchase — outcome tracking | Outcome form available (no tracking paywall) | □ | □ |
| 8.6 | Dashboard unlocked | Decision Dashboard shows full stats, bar chart, tracked outcomes | □ | □ |
| 8.7 | Restore purchases | `restorePurchases()` via RevenueCat → customerInfo entitlements checked → premium_unlocked set | □ | □ |
| 8.8 | Restore from fresh install | Install on new device, log into same Google account, restore → premium re-activated | □ | □ |
| 8.9 | Refresh entitlements on app relaunch | `refreshEntitlements()` called; checks customerInfo.entitlements.active for 'lifetime_access' and 'pro_plus' | □ | □ |
| 8.10 | localStorage fallback when RevenueCat unavailable | If `window.Capacitor.Plugins.Purchases` not found, falls back to localStorage check | □ | □ |
| 8.11 | RevenueCat init with hardcoded API key | API key `goog_pfkOiYhnnEgDTPgSChROlrVFSpg` embedded in index.html — verify it's correct in RC dashboard | □ | □ |

---

## 9️⃣ Sound Effects

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 9.1 | Card flip sound plays on reveal | `flipSound.play()` called in `displayReading()` after cards load; file: `sounds/card-flip.mp3` | □ | □ |
| 9.2 | Card shuffle sound plays on spread selection | `shuffleSound.play()` called in `selectSpread()`; file: `sounds/card-shuffle.mp3` | □ | □ |
| 9.3 | Sound files are bundled | `public/sounds/card-flip.mp3` and `card-shuffle.mp3` exist and are non-empty | □ | □ |
| 9.4 | Sound plays on actual device (not emulator) | AudioContext / HTML5 Audio works in Capacitor WebView | □ | □ |
| 9.5 | Sound failure doesn't break UX | `.catch(() => {})` on play() — audio autoplay policy rejection is silently handled | □ | □ |
| 9.6 | No sound bleed / overlap | Rapid spread switching doesn't stack shuffle sounds indefinitely | □ | □ |

---

## 🔟 Performance

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 10.1 | Cold start time | Splash to interactive WebView: < 3 seconds on mid-range device (2021+) | □ | □ |
| 10.2 | Initial card data load time | Fetch `tarotapi.dev/api/v1/cards` (78 cards) completes in < 5 seconds on 4G | □ | □ |
| 10.3 | Tarot drawing latency | `/cards/random?n=N` + `/api/reading` round-trip: < 8 seconds total (loading animation should cover) | □ | □ |
| 10.4 | Encyclopedia scroll smoothness | Grid of 78 cards scrolls without jank on mid-range device | □ | □ |
| 10.5 | Memory usage — idle | < 100 MB RAM after launch | □ | □ |
| 10.6 | Memory usage — after 10 readings | No visible memory leak; tabs switch smoothly | □ | □ |
| 10.7 | Tab switching performance | Switch between all 5 tabs: < 300ms response | □ | □ |
| 10.8 | Image loading performance | 78 card thumbnails load progressively without blocking UI | □ | □ |
| 10.9 | Starfield animation performance | 60 animated DOM elements (twinkle keyframes) don't cause frame drops | □ | □ |
| 10.10 | localStorage size impact | readingHistory up to 50 entries + dailyCard + dailyDraws + premium keys → < 50 KB total | □ | □ |

---

## 1️⃣1️⃣ Edge Cases

| # | Test Case | Expected Result | PASS | FAIL |
|---|-----------|------------------|------|------|
| 11.1 | Rapid double-tap on "Draw Your Cards" | Button should debounce or first draw should complete; no duplicate paywall or double fetch | □ | □ |
| 11.2 | Rapid spread switching during animation | Switching spreads mid-animation shouldn't break card display | □ | □ |
| 11.3 | Device orientation change (portrait → landscape) | `android:configChanges="orientation|screenSize|...` configured → Activity not recreated; WebView reflows | □ | □ |
| 11.4 | System back button behavior | Back button should close modals first (paywall, card detail, outcome), then navigate app, not exit | □ | □ |
| 11.5 | Very long question text (1000+ chars) | Textarea accepts long input; question displayed in reading header without overflow/truncation | □ | □ |
| 11.6 | Very long user name (500+ chars) | Name field accepts; reading header displays without breaking layout | □ | □ |
| 11.7 | Special characters in question | Emoji, HTML tags, quotes in question input → rendered safely as text (no XSS) | □ | □ |
| 11.8 | API failure on `/cards/random` | 4xx/5xx from tarotapi.dev → retry then show error; form re-shown | □ | □ |
| 11.9 | API failure on `/api/reading` | Falls back to template `getReading()` with localStorage card data — user still gets a reading | □ | □ |
| 11.10 | localStorage full / quota exceeded | 5MB quota typical; if exceeded, writes fail silently; catch in try/catch not present — verify graceful degradation | □ | □ |
| 11.11 | Midnight boundary crossing | Start draw just before midnight, complete after → daily draw count uses `toDateString()` which is correct; no off-by-one | □ | □ |
| 11.12 | Multiple tabs open (testing only) | App in Capacitor WebView — only 1 instance; no concern | □ | □ |
| 11.13 | Google Play Billing not available (e.g. Fire tablet) | RevenueCat init fails gracefully; paywall button shows error "Payment system not available" | □ | □ |
| 11.14 | App in background → foreground | App should resume without losing state (question form, reading display, etc.) | □ | □ |
| 11.15 | Press hardware back during API request | Loading state active → back should either cancel fetch or let it complete; no crash | □ | □ |
| 11.16 | Clear app data / reinstall after premium purchase | Premium should restore via RevenueCat (`restorePurchases()`) — entitlement not stored client-side only | □ | □ |

---

## Test Environment Notes

| Item | Details |
|------|---------|
| **Devices** | Google Pixel 7 (API 33), Samsung Galaxy A13 (API 31), Android emulator (API 22 / API 23) |
| **Network** | 4G (real), WiFi, Airplane Mode (offline) |
| **Tools** | adb logcat, Chrome DevTools via `chrome://inspect`, Android Studio Profiler |
| **APK** | `android/app/build/outputs/apk/debug/app-debug.apk` or release APK |
| **Build** | `npx cap sync android && cd android && ./gradlew assembleDebug` |

## Key Compliance / Code Issues to Flag During Testing

- **GA4 removal** (lines 12–22 of index.html): Verify `<script async src="https://www.googletagmanager.com/gtag/js?id=G-DKXVEZH3DL">` and associated `gtag()` calls are removed — compliance docs deny analytics
- **RevenueCat upgrade**: v6.1.2 → v13.x required (API breaking changes in between)
- **minSdkVersion bump**: Currently 22 in `android/variables.gradle`; must be bumped to 23
- **API key exposure**: RevenueCat API key (`goog_pfkOiYhnnEgDTPgSChROlrVFSpg`) is hardcoded in index.html client-side — this is expected for RevenueCat SDK but verify no abuse possible
- **Billing library**: `com.android.billingclient:billing:6.0.1` directly in build.gradle dependencies (line 52) — verify compatibility with RevenueCat plugin

---

*Plan prepared for Decision Tarot v4.5. Not a test execution report — use this checklist with PASS/FAIL columns during manual QA pass.*
