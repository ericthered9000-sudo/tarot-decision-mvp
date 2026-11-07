# Decision Tarot — build notes (Scenario D + AI endpoint)

Shared log for the Decision Tarot v5.1.0 build. Sections are appended by the card that owns
the work: `AI endpoint` (t_fd7f66e2), Scenario D probes / listing copy (t_57ab33a4),
QA battery (t_1e8f4279).

---

## AI endpoint

Card: `t_fd7f66e2` — deploy the AI reading function and point the app at an absolute URL.
Run date: 2026-10-28 (CDT).

### What was actually broken

| Symptom | Root cause |
| --- | --- |
| `POST /api/reading` → 404 NOT_FOUND | The function had never been deployed. |
| Site root served a 76,780-byte `index.html` with no AI call | The last production deploy predated the AI client wiring. |
| App in the APK always fell back to the template | `callAiReading()` fetched the **relative** path `/api/reading`, which inside Capacitor resolves to `https://localhost/api/reading`. |
| `vercel --prod` refused to build at all | `vercel.json` used `"version": 2` + `"public": true` — the current Vercel schema rejects both (`should NOT have additional property 'public'`). |

### Deployment

- Project: `tarot-decision-demo` (`prj_UYkm9nSKw1ku3UprQC479EAf7sV6`, team `team_KzUNvwNGXBkAV5lBjnWDPGwf`)
- Deploy: `npx vercel --prod --yes --scope ericthered9000-3193s-projects --token "$(cat /home/james/.openclaw/workspace/keystone-digital/.vercel-token)"`
- Deployment id: `dpl_E7McBczdxpo46Zd9aWfF1uopVX6H`
- Production alias: **https://tarot-decision-demo.vercel.app**
- Immutable build URL: https://tarot-decision-demo-jxyrxz1i1-ericthered9000-3193s-projects.vercel.app

`vercel.json` was rewritten to the modern (non-legacy) schema:

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "buildCommand": null,
  "outputDirectory": "public",
  "cleanUrls": true,
  "trailingSlash": false,
  "functions": {
    "api/reading.js": { "memory": 512, "maxDuration": 15 }
  }
}
```

`buildCommand: null` + `outputDirectory: public` replaces the old implicit behaviour: static
site is served out of `public/`, and `api/reading.js` deploys as a Node serverless function.

### Verified response shapes (production, HTTP)

No-key path (current production state — no `GROQ_API_KEY` is set on the project):

```
$ curl -s -X POST https://tarot-decision-demo.vercel.app/api/reading \
    -H "Content-Type: application/json" \
    -d '{"cards":[{"name":"The Fool","orientation":"upright","position":"Situation","meaning_up":"New beginnings"}],
         "question":"Should I take the new job?","name":"","spread_name":"One Card"}'
HTTP 200
{"readings":[{"position":"Situation","text":"**The Fool** — Situation. New beginnings"}],
 "synthesis":"Energy flows smoothly through this reading. ... regarding \"Should I take the new job?\". Trust the path ahead.",
 "ai_generated":false,
 "message":"AI reading unavailable — using template fallback. Set GROQ_API_KEY env var."}
```

- `OPTIONS /api/reading` → `200`, `access-control-allow-origin: *`, `access-control-allow-methods: POST, OPTIONS`.
- `GET /api/reading` → `405 {"error":"Method not allowed. Use POST."}`
- Missing `question` → `400 {"error":"Missing or invalid \"question\" field."}`
- No-key latency: `200` in **0.31s** (no Groq round-trip at all).

**Env plumbing proven end-to-end without a real key.** A deliberately invalid
`GROQ_API_KEY=placeholder-invalid-key-james-must-replace` was set on the project, redeployed, and
the endpoint answered `200` in **1.39s** with
`"message":"AI generation fell back to template: Groq API error: 401 Unauthorized — Invalid API Key"`.
That proves the dashboard env var reaches the function and that Groq is really called — the only
missing piece is a valid key value. The placeholder was then **removed** and the project redeployed;
`vercel env ls production` is now empty and production is back to the instant template fallback.

With-key success path: **not testable on this machine** — there is no Groq key anywhere (repo
`.env`s, shell env, `~/.config/groq`, `~/.groq`, `~/.hermes` config). The success path was proven
locally against a stubbed Groq response instead: JSON output → `ai_generated: true` with real
per-card text; upstream 500 → 3 attempts → template fallback with `ai_generated: false`;
401 → 1 attempt (not retried) → template fallback in 5ms. **James must add the key** (below) to
make it live.

### Function hardening (`api/reading.js`)

The function as found would have produced *worse* copy than the template once a key existed:

- `parseReading()` only ever emitted `**Card** — Position` headers in the free-text path and
  discarded the model's actual prose.
- The prompt never told the model what spread position each card occupied (the card objects come
  from `tarotapi.dev`, which has no position field), so the model was being told
  `Position: undefined`.
- `MAX_RETRIES` was declared but no retry loop existed.

Fixed inside `api/reading.js` only:

1. Prompt now demands a single JSON object `{readings:[{position,text}], synthesis}` and the
   request sets `response_format: {"type":"json_object"}`.
2. `parseReading()` is three-pass: JSON → free text split on card names (pulling a trailing
   `Synthesis:` paragraph into `synthesis`) → per-card template lines with the prose kept as the
   synthesis. A chatty model can no longer produce empty reading text.
3. Positions are derived server-side from the card count (1 / 3 / 7 spreads) or `card.position`
   when supplied.
4. Real retry loop (`MAX_RETRIES = 2`, i.e. 3 attempts) with a per-attempt 9s abort so the whole
   handler stays under the 10s Hobby cap; `maxDuration` in `vercel.json` is 15s. Retries are
   limited to 429 / 5xx / network errors — a 401 (bad key) or 400 fails identically every time and
   would only triple latency before the fallback.
5. Cards capped at 10 per request (a 78-card "spread" would blow the context/timeout).
6. Response shape unchanged: `{readings[], synthesis, ai_generated, message?}`, always `200` for
   a well-formed request — the client's template fallback contract is intact.

CORS decision: kept `Access-Control-Allow-Origin: *`. Narrowing to the Vercel domain +
`https://localhost` is not worth it — the endpoint returns non-secret tarot text, and an origin
allowlist is exactly the kind of thing that silently breaks on a future Capacitor origin
(`capacitor://localhost` on iOS, `http://localhost` on some Android builds). Noted, not changed.

### App change (`public/index.html`)

Added one constant + a premium gate, both inside the `callAiReading()` block:

```js
// ========== AI READING ENDPOINT ==========
// ABSOLUTE URL on purpose. A relative '/api/reading' inside the Capacitor APK
// resolves to https://localhost/api/reading, which 404s and silently drops every
// user to the template fallback. This constant is the single source of truth —
// repoint the app by changing this one line.
const AI_READING_ENDPOINT = 'https://tarot-decision-demo.vercel.app/api/reading';
```

`fetch('/api/reading', ...)` → `fetch(AI_READING_ENDPOINT, ...)`.

Gate (Scenario D: AI text is a premium entitlement; the card template stays free):

```js
async function callAiReading(cards, question, name, spread_name) {
  if (!isPremium) {
    return getReading(cards, question, name) + getAiUnlockPitch();
  }
  try { ... }
}
```

`getAiUnlockPitch()` renders a bordered card with "AI-written readings are a premium feature",
an explanatory line, and an `Unlock AI Readings` button → `showPaywall('ai_reading')`.
The existing `catch` → `getReading()` template fallback is untouched, so offline / no-key /
endpoint-down behaviour is unchanged.

Paywall copy: the lifetime card gained a `✓ AI-written readings` bullet to match the monthly
card's existing `✓ AI-powered interpretations` claim — the premium gate now exists, so the copy
is accurate.

`npx cap sync android` run after the edit; `public/index.html` and
`android/app/src/main/assets/public/index.html` are byte-identical, and no relative
`/api/reading` remains in the shipped assets.

### James — the one manual step

The endpoint currently template-fallbacks on every call. To turn the AI on:

1. Get a Groq API key (https://console.groq.com/keys) — free tier is enough for this volume.
2. Vercel dashboard → project **tarot-decision-demo** → **Settings** → **Environment Variables**
   → add `GROQ_API_KEY` = your key, environments: **Production** (add Preview too if you want).
3. **Redeploy** — env var changes do not apply to an existing deployment. Easiest: Deployments →
   the latest → ⋯ → **Redeploy** (no code change needed).
4. Sanity check:

```
curl -s -X POST https://tarot-decision-demo.vercel.app/api/reading \
  -H "Content-Type: application/json" \
  -d '{"cards":[{"name":"The Fool","orientation":"upright"}],"question":"Should I take the new job?","name":"James","spread_name":"Single Card"}'
```

`"ai_generated": true` = live. `false` + a `message` = key missing/not redeployed (the message
includes the Groq error).

Note: AI text is premium-only in the app, so a fresh install still shows the template + unlock
pitch until the premium path is purchased/unlocked.

### Verification log

| Check | Result |
| --- | --- |
| `node --check api/reading.js` | OK |
| `node --check` on the extracted inline `<script>` of `public/index.html` | OK (63,241 bytes of JS) |
| Local handler harness, 8 cases (no key, OPTIONS, GET, bad payload, JSON reply, fenced prose, Groq 500 ×3, single card) | all as specified |
| Local 401 case | 1 attempt only, `200` template fallback in 5ms |
| `vercel --prod` (final) | production alias `https://tarot-decision-demo.vercel.app`, READY |
| Root `GET /` | `200`, 97,486 bytes, **byte-identical (md5 `bf76be70…`) to `public/index.html`**, contains `callAiReading`, `AI_READING_ENDPOINT`, `getAiUnlockPitch` |
| `POST /api/reading` | `200`, `ai_generated:false` template shape (no key), 0.31s |
| `POST /api/reading` with invalid key set | `200`, `ai_generated:false` + `401 Unauthorized` in `message`, 1.39s (placeholder then removed) |
| `OPTIONS /api/reading` | `200` + CORS headers |
| `cap sync android` drift | none — `public/index.html` and `android/app/src/main/assets/public/index.html` identical (md5 `bf76be70…`) |
| `grep -rn "fetch('/api/reading'"` in shipped assets | no hits |

### Notes for the other cards

- `t_57ab33a4` (Scenario D) also edits `public/index.html` and also runs `cap sync`. This card's
  edits are confined to the `callAiReading()` block (constant + gate) plus one paywall bullet
  (`✓ AI-written readings` on the lifetime card). If Scenario D changes the file after this deploy,
  run `npx cap sync android` and `npx vercel --prod` once more so the APK assets and the served site
  match the final tree.
- `showPaywall('ai_reading')` is called with a new `source` value; `showPaywall` currently ignores
  `source` beyond the default, so nothing else needed.
- Free users never hit the network for a reading: the gate returns before `fetch`. QA check 14
  ("free user sees template + unlock pitch") is satisfied by `getAiUnlockPitch()`.

---

# Scenario D — implementation + device verification (card `t_57ab33a4`)

Supersedes the pre-hold plan in `t_a7c4b9b4`. Everything below was produced on `master` with the
edits uncommitted; nothing was uploaded to Play, and AdMob stayed in test mode the whole time.

## 1. What Scenario D changed in this card

| # | Change | Where |
| --- | --- | --- |
| 1 | `tarot_lifetime` **$19.99 → $9.99** (paywall card + `PRODUCTS.lifetime` badge/price) | `public/index.html` (~L1320, ~L1553) |
| 2 | `tarot_monthly` **$9.99 → $4.99** (`PRODUCTS.monthly` price + badge + paywall copy) | ~L1302, ~L1566 |
| 3 | Yearly **removed from the paywall UI** — 2-tier layout (Lifetime hero + Monthly). The `yearly_base` constant is kept with a comment saying it is deactivated Play-side; nothing queries it. | ~L1306-1317, ~L1590 |
| 4 | **Free-tier recut**: unlimited 1-card + unlimited 3-card, **1 Celtic Cross (7-card)/day**. `getDailyDrawCount()` now counts only 7-card draws, under a new key `celticCrossDraws` (legacy `dailyDraws` no longer read). | ~L1625-1665 (counter), gates at ~L1768 `selectSpread()` and ~L1845 `drawCards()` |
| 5 | Journal + outcome dashboard confirmed **free** (no `isPremium` reference anywhere in their code paths); listing copy corrected in §6 below | `loadHistory()`, `loadDashboard()` |
| 6 | **`Allow reversed cards` toggle** (settings, default ON) → `allow_reversed` localStorage key + single `pickOrientation()` helper used by the draw path. OFF forces all-upright. | ~L1085 (checkbox), ~L1667-1690, ~L1897 (draw) |

Also fixed during verification (see §5): `checkPremium()` no longer revokes a locally-granted
access-code entitlement when Play Billing reports `isPremium:false`.

## 2. Unit tests — the "isPremium responds to a simulated INAPP purchase row" evidence

```
cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --max-workers=1
```

Result (`android/app/build/test-results/testDebugUnitTest/*.xml`, run 2026-10-28T01:05:20Z):

| Suite | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `com.keystonedigital.decisiontarot.plugins.billing.BillingPremiumTest` | **5** | 0 | 0 |
| `com.getcapacitor.myapp.ExampleUnitTest` | 1 | 0 | 0 |

- `simulatedInappLifetimePurchaseMakesUserPremium` — an INAPP `tarot_lifetime` purchase row flips
  `isPremium` to true (the dual-type read path, not just the SUBS path).
- `productTypeConstantsMatchPlayBillingApi`, `mergeKeepsBothTypesAndIgnoresNulls`,
  `pendingPurchaseIsNotPremiumYet`, `yearlyBaseSubscriptionStillGrantsPremium` — all pass.

## 3. Build + asset freshness

| Check | Result |
| --- | --- |
| `npx cap copy android` | OK, 21ms |
| `diff -rq public android/app/src/main/assets/public` | only `cordova.js` / `cordova_plugins.js` (Capacitor-generated, expected) |
| md5 `public/index.html` vs `android/.../assets/public/index.html` | identical — `24a9313fdd456176629bc727dcb9e849` |
| md5 of `assets/public/index.html` **inside the APK** (`unzip -p … \| md5sum`) | `24a9313f…` — the APK really carries the final file |
| `./gradlew assembleDebug` | `BUILD SUCCESSFUL` (final run 1s incremental; full clean build earlier: `BUILD SUCCESSFUL in 2m 11s`) |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk`, 12,802,435 bytes (built 20:07, carries `24a9313f…`) |
| Version on device (`dumpsys package`) | `versionCode=63`, `versionName=5.1.0` ✅ unchanged |
| `grep -rn tarot_yearly public android/app/src` | **no hits** ✅ |
| Relative `/api/reading` in shipped assets | none — the only occurrences are the "why absolute" comment and the absolute `AI_READING_ENDPOINT` constant |

## 4. Emulator battery (real device run, not code reading)

Device: `emulator-5554`, `com.keystonedigital.decisiontarot` v63/5.1.0, debug APK, AdMob test ads.
Driven with `adb` + the WebView DevTools protocol (`adb forward tcp:9222
localabstract:webview_devtools_remote_<pid>`, harness `/tmp/dt-cdp-min.py`) so the checks run inside
the **real installed webview** against the **real shipped assets**; screenshots via `adb exec-out
screencap`. Known emulator limit: **no Play Billing service** —
`Billing connection failed: Billing service unavailable on device`, so no live purchase/query flow
can be executed here (structural + unit-test proof instead, noted per check).

### A. Free tier (fresh install, `pm clear`)

| # | Check | Result | Evidence |
| --- | --- | --- | --- |
| A1 | Unlimited 1-card + 3-card for free users | **PASS** | 12 draws on the final APK (6×1-card, 6×3-card): `paywall=false` on all 12, rendered reading text 652–1190 chars, `celticCrossDraws` stayed `0`. Plus 12 more draws earlier in the same session and 12 on the pre-fix build — 0 paywalls in every batch. |
| A2 | Celtic Cross: first/day OK, second/day paywalled, premium clears it | **PASS** | `celtic1 → {paywall:false, rendered 1941 chars, count 0→1}`; `celtic2 → {paywall:true, count stays 1}`. Both gates fire independently: `drawCards()` and `selectSpread(7)` each block (`spreadGateBlocks: true`). |
| A3 | Journal + dashboard usable while free | **PASS** | `loadHistory()` → 27 `.history-item` rows, 3,780 chars of real entries ("10/27/2026 · Three-Card Spread … Track Outcome / View Reading"). `loadDashboard()` → empty state, then after one `outcome:'good'` row: "1 Decision Tracked · 1 Good · 0 Neutral · 0 Bad" + 1,999-char chart. No paywall in either path, `isPremium:false`. |
| A4 | Paywall copy matches reality | **PASS** | Verbatim: "Remove ads and unlock unlimited Celtic Cross readings. Journal, outcome tracking and the daily card are always free." No claim that journal/outcome are paid. |

### B. Banner policy (the account-suspension class)

| # | Check | Result | Evidence |
| --- | --- | --- | --- |
| B5 | Free path: banner renders at top, content shifted below, no overlap at idle / mid-scroll / during a reading | **PASS** | logcat: `banner requested (testing=true)` → `bannerAdSizeChanged` (64dp) → `[AdMob] content inset = 168px (banner 64dp)`; computed `body { padding-top: 188px }` = 20px + 168px inset, `--admob-banner-inset: 168px`. Screenshots `/tmp/shot-final-free-idle.png`, `/tmp/shot-scroll-free2.png` (window.scrollY 600), `/tmp/shot-reading-free.png`: the scroll viewport clips content at the inset edge — nothing is drawn underneath the ad. |
| B6 | No tappable control inside the banner band | **PASS** | Banner occupies the top 320×50 creative (~y 96–222 px); the nearest controls are the tab bar at y≈900 px after the inset. No `[onclick]` element intersects the banner rect. Small note: the SDK reserves the *size-event* height (64dp = 168px) while the test creative paints 50dp, so there is a ~60px gap of background between ad bottom and content top — cosmetic, adaptive-banner behaviour, not an overlap. |
| B7 | Premium: banner never requested, inset 0, spreads unlocked | **PASS** | With the access-code entitlement persisted, restart logcat shows `[AdMob] premium user — banner never requested` and **no** `showBanner` call; DOM state `bannerRequested:false, bannerShown:false`, `--admob-banner-inset:""`, `body { padding-top: 20px }`, no banner element in the tree. 3× Celtic Cross in a row, no paywall (`isPremium:true`). Screenshot `/tmp/shot-premium.png` — no banner, header at top. |
| B8 | Consent on a US-geo emulator: no blocking dialog | **PASS** | `[AdMob] consent status=NOT_REQUIRED canRequestAds=true` → straight to the banner; no UMP form, no blocking modal. |

### C. Paywall / billing

| # | Check | Result | Evidence |
| --- | --- | --- | --- |
| C9 | Monthly $4.99, lifetime $9.99, **no yearly card** | **PASS** | `PRODUCTS` = `tarot_monthly $4.99 / $4.99/mo`, `tarot_lifetime $9.99 / $9.99 once`, `yearly_base` present as a constant only. Paywall DOM: exactly **2** purchase cards (`[onclick^=initiatePaywallPurchase]` → 2), text contains `$9.99` and `$4.99` and no `$79.99`/year. |
| C10 | Yearly constant gone / degrades gracefully | **PASS (structural)** | `grep -rn tarot_yearly public android/app/src` → 0 hits. `yearly_base` survives only in `PRODUCTS.yearly` + the `initiatePaywallPurchase('yearly')` branch, which no UI path can reach — so nothing queries a deactivated SKU at runtime; there is no "missing product" crash path left. |
| C11 | `tarot_lifetime` INAPP dual-type, structurally proven | **PASS (structural)** | `GooglePlayBillingPlugin.java`: `queryProducts` does `queryDetailsForType(ids, SUBS)` then, inside its callback, `queryDetailsForType(ids, INAPP)` and reports `"queriedTypes":"SUBS,INAPP"` (L253-283); purchase tries SUBS then falls back to INAPP (L317-335); `isPremium` queries SUBS **and** INAPP purchase rows (L397-415). Unit tests `productTypeConstantsMatchPlayBillingApi` + `mergeKeepsBothTypesAndIgnoresNulls` pass. Live query impossible here (no Play services on the emulator). |
| C12 | Reversals toggle: persists, OFF = all upright, ON = random | **PASS** | OFF → 12 draws / 34 cards, **0** reversed, and `pickOrientation(0.5)` returned upright **200/200**. Persisted across `force-stop` + relaunch: checkbox unchecked, `allow_reversed:"false"`, next draw still all-upright. ON → checkbox/ls true, 2 reversed of 12 cards (~25%, matching the 0.25 chance). Default on a fresh install: **ON** (checkbox checked, behaviour unchanged). |

### E. Regression

| # | Check | Result | Evidence |
| --- | --- | --- | --- |
| E15 | Daily Card tab, sounds, history, template fallback | **PASS** | Daily tab renders ("Today's Card / Your free daily guidance"). `flipSound`/`shuffleSound` constructed from `sounds/card-flip.mp3` + `sounds/card-shuffle.mp3`, both present in the APK (`unzip -l`). History grows as you draw (27 → 35 entries across the battery). AI gate: free user → `callAiReading()` returns the template **plus** the unlock pitch with no network call (1,619 chars, `hasPitch:true`); premium + forced-offline `fetch` → template fallback (842 chars, no throw), i.e. "endpoint unreachable" is safe. |
| E16 | Build repro + cap-sync drift | **PASS** | `assembleDebug` clean on the final tree; `public/` vs `assets/public` md5-identical (see §3). |

Not verified here (needs James's console actions): real `tarot_lifetime` / `tarot_monthly` purchase
sheets, real ad serving with production IDs, and the Groq `ai_generated:true` reading (that card's
open risk — no key in Vercel yet).

## 5. Bug found and fixed during verification — premium entitlement was clobbered on restart

`checkPremium()` did `isPremium = result?.isPremium === true;` unconditionally. The **Zeppelin access
code** is a *local* entitlement that Play Billing knows nothing about, so on the next launch a tester
who redeemed it is downgraded to free and immediately served ads (exactly the "paying/tester user
sees ads" class we must never ship).

Fix (`public/index.html`, `checkPremium()`):

```js
const codeUnlocked = localStorage.getItem('access_code_unlocked') === 'true';
isPremium = result?.isPremium === true || codeUnlocked;
```

Honest scope note: this emulator has no Play Billing service, so `checkPremium()` throws and the
`catch` path preserves the cached value — the clobber is **not reproducible on this emulator**; the
fix is defensive/correct-by-construction for real devices. A real-device check: redeem `Zeppelin`,
force-stop, relaunch → logcat must show `[AdMob] premium user — banner never requested`.
Zeppelin code, `isTesting: true` and the two-constant AdMob flip (`ADMOB_BANNER_AD_UNIT` in
`index.html` + `admob_app_id` in `strings.xml`) are all untouched.

## 6. Listing copy — corrected draft for James to paste

The current listing markets "Full reading history and journal" and "Outcome tracking" as premium.
They are free in code. Replacement text:

**Short description (77 chars, Play's limit is 80):**
`Tarot for real decisions. Free 1- and 3-card spreads, one Celtic Cross a day.`

**Full description (paste-ready):**

```
Decision Tarot turns a tarot spread into a clear next step, not a fortune.

FREE, FOREVER
• Daily card
• Unlimited one-card readings
• Unlimited three-card readings
• Full reading history and journal
• Decision dashboard — track how each decision actually turned out
• The complete card encyclopedia, upright and reversed meanings

OPTIONAL UPGRADE
• One Celtic Cross (7-card) reading every day, free. Upgrade for unlimited Celtic Cross readings
• Remove ads
• AI-written readings: a personal interpretation of your exact question

Two ways to upgrade: $9.99 once, forever — or $4.99/month, cancel anytime. No ads are ever shown to
paid members.

Your readings stay on your device. No account required.
```

**Must-match bullets:** free = daily card, 1-card, 3-card, journal, dashboard, encyclopedia; premium
= unlimited Celtic Cross, no ads, AI readings; prices **$9.99 once / $4.99 per month**; yearly is not
offered.

## 7. Reversals toggle — what QA should know

Settings → "Allow reversed cards" (checkbox, **default ON**). Storage key `allow_reversed`
(`'false'` disables). All three spreads go through `pickOrientation(reversalChance)`
(25% for 1/3-card, 35% for Celtic Cross) — when the toggle is off it returns `'upright'`
unconditionally, so the reading text, the modal meanings and the journal all show upright.
Default ON means behaviour is byte-for-byte what shipped before; nothing changes for existing users
until they touch the switch.

## 8. James — console checklist for this release

Pricing / products (Play Console → Monetize → Products):
1. **In-app products → Create** `tarot_lifetime`, name "Tarot Lifetime", price **$9.99**, Activate.
   (One-time products live on a different screen than subscriptions.)
2. **Subscriptions → `tarot_monthly` → base plan → edit price to $4.99** (0 subscribers → clean
   reprice, no grandfathering prompt).
3. **Subscriptions → `yearly_base` → Deactivate.** Use the id `yearly_base` — *not* `tarot_yearly`
   (that was the shipped-code typo this release fixes). Do **not** create/keep `tarot_yearly`.
4. Confirm nothing else references a $79.99 yearly plan in the listing text.

AdMob (condensed from `docs/admob-research.md` §1.4):
5. admob.google.com → accept terms (payment profile) → **Apps → Add app → Android** →
   `com.keystonedigital.decisiontarot`.
6. **Ad units → Add → Banner** `decisiontarot_banner_top` → copy the `/NNNN` unit id.
7. **App settings → App ID** `ca-app-pub-…~NNNN` (tilde, not slash).
8. **Privacy & messaging** → publish the GDPR message and the US-states message (UMP is inert
   until a message exists).
9. Swap both constants in one commit: `ADMOB_BANNER_AD_UNIT` (public/index.html) and
   `admob_app_id` (android/app/src/main/res/values/strings.xml), then flip
   `ADMOB_IS_TESTING = false`; `cap sync` + rebuild.

Store declarations:
10. App content → **Ads → Yes, contains ads**.
11. **Data safety** → AdMob rows (`admob-research.md` §3.2).
12. `public/privacy-policy.html` must disclose ad/device data **before** a build with real ads ships
    (already flagged by the research card — the page has not been updated yet).
13. Upload the AAB (versionCode 63, versionName 5.1.0) only after 1–12.

## 9. Notes for QA (`t_1e8f4279`) — how to reproduce

- Emulator was already up on `emulator-5554`; app installed with `adb install -r`.
- Drive the real webview, don't only read code:
  `adb shell cat /proc/net/unix | grep -o 'webview_devtools_remote_[0-9]*'` → `adb forward tcp:9222
  localabstract:<that>` → `python3 /tmp/dt-cdp-min.py <expr.js>` (harness reads `/json`, evaluates
  with `awaitPromise`, returns JSON). Screenshots: `adb exec-out screencap -p > shot.png`.
- Gotchas that cost me time: (a) `drawCards()` silently returns "Please select a decision category"
  unless `#decision-type` has a value — set it before every draw or you get fake "no paywall"
  results; (b) WebView DOM storage flushes lazily — wait ~4s after writing localStorage before
  `am force-stop`, or your entitlement/settings test restarts with empty state; (c) the emulator has
  **no Play Billing service**, so purchase-sheet checks can only be structural here.
- `public/index.html.backup`, `.backup-20260329-201906`, `.backup-2026-03-31-tarot` are stale copies
  sitting in `public/` and therefore **shipped inside the APK assets** (~170KB of dead weight, and
  three old app versions travel with the release). Recommend deleting them in a follow-up — not done
  here because they may still be wanted as local history.
- `public/index.html` is the shared hotspot with `t_fd7f66e2` (AI endpoint): that card's
  `callAiReading()` block is untouched by this one except the paywall bullet it added. If both land
  out of order, re-run `cap sync` + one `assembleDebug`.

