# QA — Decision Tarot v5.1.0 (Scenario D) monetization + AI readings

**Task:** `t_1e8f4279` (QA gate) · **Parents:** `t_57ab33a4` (Scenario D build), `t_fd7f66e2` (AI endpoint)
**QA run:** 2026-10-27 20:20–20:35 CDT · **QA:** Tessa
**Verdict:** **PASS with 1 non-blocking cosmetic defect (DEF-1) + 1 policy decision for Girz/James (DEF-2) + 1 item blocked on an external secret (GROQ_API_KEY).**
Nothing was rubber-stamped: every check below was re-run independently on the installed build. Where Bob's evidence is reused it is labelled *(build-notes §9)*.

---

## 0. Environment actually used

| Item | Value |
|---|---|
| Device | `emulator-5554`, AVD `Pixel_7_Pro`, 1080×2400 @ 420dpi (dpr 2.625) |
| CSS viewport | 411 × 838 CSS px (WebView CSS px == dp: 1080 / 2.625 = 411) |
| App | `com.keystonedigital.decisiontarot` versionCode **63** / versionName **5.1.0** |
| Shipped web asset | `index.html` md5 `24a9313fdd456176629bc727dcb9e849` (public/ == android assets/) |
| Method | Real WebView driven over CDP (`/tmp/dtq.py`, CDP `Runtime.evaluate` + `awaitPromise`), native view bounds via `uiautomator dump`, pixels via `screencap`, `logcat` for plugin events |
| Ad state | Test mode throughout: `ADMOB_IS_TESTING = true`, unit `ca-app-pub-3940256099942544/6300978111`, app id `ca-app-pub-3940256099942544~3347511713` — **no live ad was ever requested or clicked** |
| App code | **read-only** — no app file was modified by this QA run (only localStorage test state, documented in §5) |

Screenshots: `/tmp/qa-shots/01-free-idle.png` (mid-reading), `02-free-top.png` (scroll 0), `03-free-midscroll.png` (scrollY 1300), `04-journal-free.png`, `05-dashboard-free.png`, `06-premium-top.png`.

---

## 1. Free tier (A1–A4)

| # | Check | Verdict | Evidence |
|---|---|---|---|
| A1 | Unlimited 1-card and 3-card draws for a free user | **PASS** | 6/6 draws on the installed build: 3× 1-card + 3× 3-card, **0 paywalls**, readings rendered 708/733/939 and 1160/1181/1206 chars, 1/1/1 and 3/3/3 card images. `celticCrossDraws` counter unchanged at `count:1` across all six (no metering leak). Cross-ref: Bob ran 12/12 (build-notes §9). |
| A2 | Celtic Cross: first of day works, second same-day paywalls, premium clears | **PASS** | Counter reset to `{count:0}`, `selectSpread(7)` → no paywall, draw → **7 cards**, 1828-char reading, counter `0→1`. Second same-day attempt blocked on **both** gates: `selectSpread(7)` → paywall (counter stays 1) **and** forced `currentSpread=7` → `drawCards()` → paywall (counter stays 1). Premium (entitlement on): 2 consecutive Celtic draws, 7 cards each, **no paywall**, counter not incremented. |
| A3 | Journal + outcome dashboard free and functional | **PASS** | `switchTab('history')` → pane `display:block`, 200 rendered nodes, 7760 chars, **no paywall**. `switchTab('dashboard')` → "1 Decision Tracked 1 Good 0 Neutral 0 Bad", chart element present, **no paywall**. Daily Card tab renders ("The Empress … Drawn today, 10/27/2026"), no paywall. |
| A4 | Paywall copy matches reality | **PASS** | Modal text verbatim: *"Unlock Full Access — Remove ads and unlock unlimited Celtic Cross readings. **Journal, outcome tracking and the daily card are always free.**"* Lifetime tier: *"$9.99 one-time • pay once, yours forever ✓ Removes ads forever ✓ Unlimited Celtic Cross readings ✓ AI-written readings ✓ No subscription"*. No claim gates the journal or outcome dashboard. |

## 2. Banner policy (B5–B8) — the account-suspension class

**Native geometry measured directly (this is the part that matters):**

```
FREE   :  WebView   bounds [0,136][1080,2337]   (838.5 CSS px tall)
          AdView    bounds [0,136][1078,304]    (168 phys px = 64 CSS px tall)
PREMIUM:  WebView   bounds [0,136][1080,2337]
          AdView    absent from hierarchy
```

The AdView and the WebView **share the same top edge (y=136)** and `innerHeight` is **838 in both states** — so the banner is a native view painted *over* the WebView's top 64 CSS px, it does **not** resize the WebView. The app compensates with `body { padding-top: calc(20px + var(--admob-banner-inset)) }`.

| # | Check | Verdict | Evidence |
|---|---|---|---|
| B5 | Banner at top, content shifted below it, no content hidden at rest | **PASS (policy property) / DEF-1 (amount is wrong)** | At scroll 0: first content child sits at exactly `top = 188` CSS px, DOM scan for anything painting inside the reserved band → **0 intruders**. Screenshot `02-free-top.png`: opaque white test banner 0–114 CSS px, dark app content starts immediately below, app title fully visible. **But** the reserved inset is **168 CSS px for a 64 CSS px banner** — see DEF-1. |
| B5-adj | No overlap at *any scroll position* | **PASS with documented caveat → DEF-2** | Scrolled content does pass **behind** the fixed native banner (at scrollY 1300, `bodyRectTop = −1300`, and `03-free-midscroll.png` shows the paragraph cut exactly at the ad's bottom edge). This is the standard fixed-banner-over-scrolling-content pattern and is exactly what `docs/admob-research.md` §2.2 prescribes (pad the web root by the banner height). No content is *permanently* obscured — the 188 px top padding guarantees everything can be scrolled into view. Flagged as DEF-2 for an explicit accept/fix decision, because §3.4.2 of the same doc quotes Google's "ads should not float or hover over app content". |
| B6 | Banner not adjacent to tappable controls | **PASS** | At scroll 0 the only content below the ad is the non-interactive title block (crystal ball + "DECISION TAROT" + tagline); the nearest tappable control (nav bar Draw/Daily/Cards/Past/Dashboard) sits far below. On the free tier the ad's bottom edge is 114 CSS px and the nav bar is at ~370 CSS px — ~256 CSS px of dead space between them. |
| B7 | Premium: banner never requested, inset returns to 0, layout clean | **PASS** | logcat after force-stop + relaunch with entitlement on: `[AdMob] premium user — banner never requested` (index.html:1537); `grep -c "banner requested"` → **0**; no `AdView` in the native hierarchy at all; `bodyPaddingTop: 20px`, `containerTop: 20`; screenshot `06-premium-top.png` shows the header immediately under the status bar. |
| B8 | Consent on US-geo emulator | **PASS** | `[AdMob] consent status=NOT_REQUIRED canRequestAds=true` (index.html:1483), **no blocking dialog**, then `bannerAdLoaded` → `bannerAdImpression` — ads load after the consent check as required. |

## 3. Paywall / billing (C9–C12)

| # | Check | Verdict | Evidence |
|---|---|---|---|
| C9 | Monthly $4.99, lifetime $9.99, no yearly card | **PASS** | Paywall DOM: exactly **2** purchase buttons (`onclick="initiatePaywallPurchase(...)"`), copy "$9.99 one-time" + "$4.99 per month • cancel anytime". Full modal text contains **no** `79.99`, **no** "yearly/annual/per year". |
| C10 | No `tarot_yearly`; `yearly_base` degrades gracefully | **PASS** | `grep -rn "tarot_yearly" public android/app/src` → **0 hits**. `yearly_base` survives only as a commented, unreachable constant (index.html:1292–1311) with the intent documented in-line: *"DEACTIVATED Play-side — intentionally NOT offered in the paywall UI"* — which the card allows. No runtime query is issued for it, so there is nothing to degrade. |
| C11 | `tarot_lifetime` INAPP dual-type query | **PASS** *(build-notes)* | Emulator has no Play Billing service, so this is structural only: `queryProducts` runs SUBS-then-INAPP, `isPremium` reads SUBS+INAPP rows, 5/5 `BillingPremiumTest` unit tests pass (build-notes §9). Purchase flow not attempted against a non-existent product — correct call, the sheet cannot open here. |
| C12 | Reversals toggle persists, OFF forces upright, ON restores random | **PASS** | Toggle OFF → `allow_reversed:"false"`, checkbox false: **6/6 cards across 4 draws (3×1-card + 1×3-card) all "Upright"**, 0 reversed. Toggle ON → **2/6 cards reversed** (~33%, expected ~25% random) — random behaviour restored. Setting persists in localStorage. Cross-ref: Bob 34/34 + 200/200 `pickOrientation` (build-notes §9). |

## 4. AI readings (D13–D14)

| # | Check | Verdict | Evidence |
|---|---|---|---|
| D13a | Prod endpoint graceful fallback with no key | **PASS** | `POST https://tarot-decision-demo.vercel.app/api/reading` → **HTTP 200 in 0.874 s**, `ai_generated:false`, per-position `readings[]` + `synthesis` present, `message:"AI reading unavailable — using template fallback. Set GROQ_API_KEY env var."` No 5xx, no crash, correct shape. |
| D13b | With James's Groq key → `ai_generated:true`, non-template text | **BLOCKED (external)** | `GROQ_API_KEY` is still unset in Vercel Production, so this **cannot** be verified by QA. Not a code failure — it is James's pending action (Vercel → Settings → Environment Variables → Production, then **Redeploy**). Re-run this one check after the key lands. |
| D14a | Endpoint constant is absolute, no relative `/api/reading` in shipped assets | **PASS** | `AI_READING_ENDPOINT = "https://tarot-decision-demo.vercel.app/api/reading"` read from the live app; `typeof callAiReading === "function"`; no relative `/api/reading` fetch in the shipped `index.html`. |
| D14b | Premium user sees AI text; free user sees template + pitch | **PASS (gate) / BLOCKED (AI text)** | Free user: reading is the template + explicit pitch *"AI-written readings are a premium feature … Unlock to have a reader write it for you personally …"* + "Unlock AI Readings" button, and `callAiReading()` returns before any network call. Premium user: **no** unlock pitch, full reading renders. However the text premium sees is still the **template**, because the server has no key — so "AI-written text" specifically is blocked on D13b. |

**Observation (not a card requirement):** the server-side keyless fallback is generic boilerplate — it returned "Forward movement and clarity" for *The Tower* and the same phrasing for *The Star*. The in-app template is far richer (real card meanings, 700–1800 chars). If the key ever expires in production, premium users silently get much weaker text than the in-app template. Worth aligning the server fallback with the client template.

## 5. Regression (E15–E16)

| # | Check | Verdict | Evidence |
|---|---|---|---|
| E15 | Daily card, history persistence, template fallback path | **PASS** | Daily Card tab renders The Empress with "Drawn today, 10/27/2026". `readingHistory` persists — 50 entries after this run's draws (was 27 → 35 in Bob's run), journal pane rendered it. Template fallback renders correctly — exercised live by every reading in this run, since the endpoint currently returns `ai_generated:false` (the same code path as an unreachable endpoint). Card-flip/shuffle audio present in `assets/public/sounds` *(playback: build-notes §9)*. |
| E16 | `assembleDebug` clean + no `cap sync` drift | **PASS** | `./gradlew assembleDebug` → **exit 0**; `app-debug.apk` 12,802,435 B. `md5sum public/index.html android/app/src/main/assets/public/index.html` → **both `24a9313fdd456176629bc727dcb9e849`** (0 drift). |

---

## 6. Defects and decisions

### DEF-1 — Banner inset is 2.6× too large (cosmetic, non-blocking) · owner: Bob
The inset is computed as `height_dp × devicePixelRatio` and then applied as a **CSS** px padding. In an Android WebView at `width=device-width, initial-scale=1` **1 CSS px already equals 1 dp**, so the ×dpr step double-converts.

Measured proof:
- logcat: `[AdMob] content inset = 168px (banner 64dp)` (index.html:1466)
- native bounds: AdView is **168 physical px = 64 CSS px** tall
- resulting CSS: `bodyPaddingTop = 188px` (= 20 + 168), so the app header starts **188 CSS px** below the WebView top while the ad only covers **64 CSS px** → **104 CSS px (~325 physical px, ~12% of the 838 px viewport) of dead space** on every free-tier screen.
- Visual confirmation: on the free tier the "DECISION TAROT" title begins at ~630 phys px; on premium the identical header begins at ~188 phys px. Nothing is broken, but the free tier looks like it has a hole under the ad.
- **Fix:** set the inset to `size.height` (no dpr multiply) — i.e. `padding = 20 + 64 = 84px`. This is a one-line change at index.html:1466 plus `applyBannerInset()`. Note `docs/admob-research.md` §2.2 prescribes `height * devicePixelRatio + margin`, so the **doc** needs the same correction or this will regress.
- Not policy-violating: over-reserving pushes content *further* from the ad, which reduces accidental-click risk. It is a UX/visual defect only.

### DEF-2 — Content scrolls beneath the fixed banner (policy decision) · owner: Girz/James
The native banner overlays the WebView (`AdView` and `WebView` both start at y=136) and the compensating `body` padding is **scrollable**, so scrolled content passes behind the ad. Evidence: at `scrollY 1300`, `bodyRectTop = −1300` and screenshot `03-free-midscroll.png` shows a paragraph clipped exactly at the ad's bottom border.

This matches the team's own research prescription (pad the web root, §2.2) and is the ordinary pattern for a fixed banner over a scrolling page — no content is permanently unreachable. However `docs/admob-research.md` §3.4.2 itself quotes Google: *"Banner ads should not float or hover over app content."* Two options:
1. **Accept** as-is (fix only DEF-1). Defensible: the ad never moves, at-rest layout is clear, and 84 px of padding keeps the header off the ad.
2. **Harden** by constraining the app to a fixed-position scroll container whose height is the area below the banner, so the ad never has content behind it. More work, removes the ambiguity entirely.

Recommendation: **option 2 before the real ad IDs are swapped in**, since that is the moment policy risk becomes real (test ads cannot get the account suspended; live ads can). Doing it also fixes DEF-1 for free because the container height is explicit.

### Blocked, not failed
`D13b` / the "AI-written text" half of `D14b` need `GROQ_API_KEY` in Vercel Production + a Redeploy. Everything on the code side of the AI path is verified: absolute constant, premium gate before any network call, graceful fallback, correct 200 shape.

### Carried over
Three stale `public/index.html.backup*` files (~174 KB) still ship inside the APK assets and are still served from the web root. Tracked by child card `t_237143c3` (Bob) — out of scope here.

---

## 7. Check summary

| Section | Checks | Result |
|---|---|---|
| A. Free tier | 4 | 4 PASS |
| B. Banner policy | 4 | 4 PASS on the policy-critical properties; 1 cosmetic defect (DEF-1) + 1 decision (DEF-2) |
| C. Paywall / billing | 4 | 4 PASS |
| D. AI readings | 4 | 3 PASS, 1 blocked on external secret (D13b) |
| E. Regression | 2 | 2 PASS |
| **Total** | **18** | **17 PASS · 0 functional FAIL · 1 blocked-on-secret** |

Test-state notes: `celticCrossDraws` was reset to 0 once to re-prove the first-of-day path (restored to `count:1` by the draw itself); the premium entitlement was injected via `access_code_unlocked` for the premium pass and **removed afterwards** — the device is back in the free, default state with `allow_reversed: "true"`.
