# Decision Tarot — Competitive Analysis & Monetization Scenarios

**Status:** research only. No code, Play Console or AdMob changes were made. All Play data pulled
2026-10-27 with `hl=en&gl=US`. Companion doc: [`admob-research.md`](./admob-research.md) (§4 pricing
scan, §5 eCPM/revenue math) — this document extends it, it does not replace it.

---

## RECOMMENDATION BOX — read this if you read nothing else

**Scenario D (hybrid): give the core reading away free, sell one $9.99 lifetime "everything"
unlock, and let the banner be the reason to buy it — not a revenue line.**

1. **The free tier is the problem, not the price.** Decision Tarot gives away *one single-card
   reading per day* (`drawCards()` → `if (dailyCount >= 1) showPaywall()`); every app in the matrix
   that has real installs gives away the core reading plus most spreads, and monetizes ads +
   an upgrade. DT charges the market leader's monthly rate ($9.99, same as Labyrinthos Premium) for
   the smallest free tier in the set. At 10+ lifetime installs (DT's own Play listing) the app is
   asking a first-time user to pay before it has ever been useful.
2. **Price cuts alone change nothing at this volume** — at ~5 installs/day, a 1–2% conversion is
   1.5–3 buyers/month, i.e. $15–$45/mo at *any* price in the $0.99–$19.99 band. Lowering the price
   is still worth doing because it costs **zero**: there are **0 subscribers**, so repricing has no
   grandfathering/migration problem.
3. **$9.99 lifetime, not $19.99.** Galaxy Tarot's entire IAP catalog on a 1M+ install app tops out
   at $14.99, and the AI-native Tarot AI's tops out at $15.99 — nothing in this category sells a
   one-time unlock above ~$16. The only paid-upfront tarot apps I found are Tarot! at $3.99 (10K+,
   4.9★) and Psychic Tarot Oracle at $9.99 (1K+, 5.0★, still shows ads). $9.99 is the top of the
   paid price band, and it is ~2 months of a $4.99 sub, so the ladder stays coherent.
4. **Deactivate the yearly SKU** ($79.99). It is 8× a $9.99 lifetime and 16× a $4.99 monthly — no
   user will ever pick it, and an unused SKU nobody can justify is worse than no SKU.
5. **The one feature that already exists and has never shipped is the AI reading.** `api/reading.js`
   (Groq / llama-3.3-70b) is written, and `callAiReading()` is wired — but it fetches the **relative**
   path `/api/reading`, which inside the Capacitor APK resolves to `https://localhost/api/reading`
   and fails, so the app silently falls back to template text. Worse: I probed the live Vercel
   deployment (`https://tarot-decision-demo.vercel.app`) and `/api/reading` returns **404**, while
   its served `index.html` (76 KB, an older build than the repo's 93 KB) does not even contain the
   call. **The "AI-Powered Readings" in DT's Play listing have never worked in any build.** Making
   them real is deploy-the-function + set `GROQ_API_KEY` + absolute URL ≈ 2–4 hours, and it is the
   cheapest real product upgrade available. Do this *before* any new feature work.

Full SKU sheet + build order in §4. Evidence below.

---

## 1. Feature-by-feature competitive matrix

### 1.0 Who is in the matrix, and why

The card named Labyrinthos, Galaxy Tarot, Trusted Tarot, Golden Thread, Tarot Simple and
"Co-Star-adjacent spirituality apps". I kept the three required tarot apps and the Co-Star
adjacent pick, and filled the remaining slots with the highest-install tarot apps Play returned for
a `tarot` search — because the point of the matrix is to see what apps *with revenue* do.

**Two deviations, both deliberate and evidenced:**

- **Golden Thread Tarot is no longer on Google Play under its old id.** `co.threadweave.goldenthread`
  now serves an unrelated gratitude-journal app titled "Golden Thread" (100+ installs, updated
  2026-10-26, no IAP, no ads), and Play search for "golden thread tarot" returns only that app.
  The tarot app (Tiny Mystic / threadweave) appears to be delisted. Prior research
  (`admob-research.md` §4) listed this package as "Golden Thread Tarot, no IAP detected" — that row
  is now stale for a reason nobody could have suspected. **Tarot! by The Fool's Dog** ($3.99 paid,
  4.9★, 711 reviews) takes its place as the "premium/paid, feature-deep" reference — see §1.4.
- **Tarot Simple** (1K+ installs, 27 reviews) is too small to teach us anything and is kept only in
  the scan table in §1.4.

Scanned: **Labyrinthos** (1M+), **Galaxy Tarot** (1M+), **Trusted Tarot** (100K+),
**Tarot – Vázquez Software** (5M+, the single highest-install pure-tarot app found),
**Lumi: AI Tarot & Horoscope** (1M+), **Tarot AI – Card Reading** (100K+),
**Tarot Card Reading and Meaning – DEN TSMO** (100K+), **Co–Star** (5M+, astrology-adjacent).

Play listings (verified 2026-10-27, `hl=en&gl=US`):

| # | App | Package |
|---|---|---|
| 1 | Labyrinthos Tarot Cards | `com.labyrinthos.app` |
| 2 | Galaxy Tarot | `com.hemisphere3.tarot` |
| 3 | Trusted Tarot | `com.trustedtarot.app` |
| 4 | Tarot (Vázquez Software) | `info.vazquezsoftware.tarot` |
| 5 | Lumi: AI Tarot & Horoscope | `com.yobzh.tarot` |
| 6 | Tarot AI – Card Reading | `com.tarotreading.aitarot` |
| 7 | Tarot Card Reading and Meaning (DEN TSMO) | `com.dt.vfo` |
| 8 | Co–Star Personalized Astrology | `com.costarastrology` |
| — | **Decision Tarot (ours)** | `com.keystonedigital.decisiontarot` |

### 1.1 Feature matrix

Legend: **Y** = shipped and visible on the listing/site · **~** = partial/limited · **N** = absent ·
**?** = not stated publicly (I will not guess). All competitor values are from the live Play
listing text unless noted; DT's column was read from the shipped source, not from memory (see §1.3).

| Feature | **DT** | Labyrinthos | Galaxy | Trusted | Tarot (Vázq.) | Lumi | Tarot AI | TCRM (DEN) | Co–Star |
|---|---|---|---|---|---|---|---|---|---|
| Spreads offered | **3** (1, 3, 7 Celtic Cross) | ~50+ (listing body; meta says 75+) | "classic spreads", count ? | 25 premium + classic set; Celtic Cross | **5** (Classic 3, Path of the Day 4, Couples, Card of Day, Yes/No) | "single to nine-card", count ? | "predefined spreads" by category (love, money, travel, moon…), count ? | 50+ (Daily, Yes/No, PPF, Celtic Cross, Relationship Cross + custom) | n/a (no tarot) |
| Daily card | **Y** (own tab) | Y | Y (Card of the Day) | Y (1 daily reading) | Y (Card of the Day) | Y | ? | Y (Daily Card spread) | n/a |
| Journal / history | **Y** local, 50 readings max | Y (journal + notes) | ? | ? | N | ? | ? | Y ("Save your readings… history") | n/a |
| Outcome tracking | **Y — unique in this set** | N (journal only) | N | N | N | N | N | N | N |
| Reversals | **Y, automatic** (25% / 35% at random, no user toggle) | Y | ? | Y (enhanced readings with reversed interpretations) | N | ? | ? | ? | n/a |
| AI reading | **~ code exists, broken in APK** (§1.3) | N | N | N | N | **Y** (AI interpretation + AI oracle chat) | **Y** (chat with AI "readers") | **Y** (AI psychic chat) | Y (human astrologers + AI, not tarot) |
| Lessons / learning | **~** card encyclopedia (78 meanings, upright + reversed) | **Y** quizzes, lenormand, runes, astrology | ~ ("Tarot Learning, Simplified") | Y (card meanings library) | Y (22 Major Arcana + quiz game, 198 questions) | Y (full card guide) | Y (meanings for all 78) | Y (quiz mode) | Y (learn your birth chart) |
| Cloud sync / account | **N** (localStorage only) | Y | ? | ? | ? | ? | ? | N | **Y** (friends, charts) |
| Notifications | **N** | ? | ? | ? | ? | ? | ? | ? | **Y** (daily push by horoscope) |
| Widgets | **N** | ? | ? | ? | ? | ? | ? | ? | ? |
| Ads in free tier | **N today** (banner coded, test ids, not shipped) | N | **Y** | **Y** | **Y** | **Y** | **Y** | **Y** | N |
| Deck choice | **N** (one RWS deck) | **Y** (all Labyrinthos decks) | ~ | ~ (one borderless deck) | Y (one deck) | Y (card backs) | Y (Marseille / RWS) | Y (?) | n/a |
| Offline | **~** art bundled, but card data is fetched live from `tarotapi.dev` (§1.3) | ? | ? | ? | ? | ? | ? | ? | ? |

Read the first row and the "Ads" row together: **every high-install app in this set gives away more
than DT does and monetizes with ads + an upgrade. DT gives away less and monetizes with nothing but
a subscription.** That inversion is the finding of this whole document.

### 1.2 Monetization matrix

| App | Installs | Rating | Reviews | IAP range (Play) | Known price points | Ads |
|---|---|---|---|---|---|---|
| **Decision Tarot** | **10+** | 4.6 | <5 | $9.99 – $79.99 | $9.99/mo, $79.99/yr | No |
| Labyrinthos | 1,000,000+ | 4.8 | 44.1K | $0.99 – $89.99 | $9.99/mo, $24.99/qtr, $89.99/yr + reading credits (~$0.10/reading)¹ | No |
| Galaxy Tarot | 1,000,000+ | 3.5 | 46.6K | $0.99 – **$14.99** | top of catalog $14.99 | **Yes** |
| Trusted Tarot | 100,000+ | 4.7 | 7.23K | $1.99 – $84.99 | 1-week / 1-month / 12-month subs + "Ask a Question" credits (prices not public) | **Yes** |
| Tarot (Vázquez) | **5,000,000+** | 4.8 | 66.6K | $7.99 – $49.99 | premium = remove ads + unlimited daily readings | **Yes** |
| Lumi | 1,000,000+ | 4.4 | 13.2K | $0.99 – $69.99 | not public | **Yes** |
| Tarot AI | 100,000+ | 4.8 | 13.4K | **$1.99 – $15.99** | first chat free, then credits/sub (price not public) | **Yes** |
| TCRM (DEN TSMO) | 100,000+ | 4.8 | 8.0K | $0.99 – $59.99 | not public | **Yes** |
| Co–Star | **5,000,000+** | 4.5 | 96.2K | $0.99 – $74.99 | not public (App Store lists a $9.99-ish monthly historically — not verified here) | No |

¹ Labyrinthos tier prices from `admob-research.md` §4 (tarotalmanac / appstoreprice.org), not from
the Play page, which publishes only the $0.99–$89.99 range.

**Revenue-scale proxies, stated honestly:** Play publishes installs, rating and review count — not
revenue or ARPU. Review counts are the only public proxy for scale, and they are skewed by prompt
timing, so treat 44.1K vs 7.23K vs <5 as an order-of-magnitude signal only.

### 1.3 What Decision Tarot actually ships (read from source, not assumed)

This matters because the card body's premise was partly wrong and because the working tree is ahead
of the last commit. Evidence: `public/index.html` (2,590 lines, working tree), `git show
HEAD:public/index.html` (2,401 lines, last commit `8064098`), `api/reading.js`, `capacitor.config.json`.

**Shipped in HEAD (`8064098`):**

- **3 spreads**, hard-coded: 1-card ("Quick Guidance"), 3-card ("Decision Clarity"), 7-card
  ("Celtic Cross" / "Deep Dive"). `selectSpread(n)` gates `num > 1` behind premium.
- **Free tier = 1 reading per day, single-card only.** `drawCards()`:
  `if (!isPremium) { if (getDailyDrawCount() >= 1) { showPaywall(); return; } }`.
  **The card body said gating is "only multi-card spreads" — that is not what ships.** There are
  *two* gates stacked: a 1/day counter *and* the multi-card lock. Net effect for a new user: one
  single-card reading, then the paywall, forever, until they pay.
- **Daily Card tab** exists (separate tab, `loadDailyCard()`), free.
- **Journal/history exists and is free**: `saveReading()` → `localStorage.readingHistory`, keeps the
  last 50, with question, cards, spread, interpretation, outcome, outcome notes, tags.
- **Outcome tracking exists and is free**: the "Decision Dashboard" (`loadDashboard()`) counts
  decisions marked good/neutral/bad and draws a chart. **No competitor in the matrix has this.**
- **Reversals exist, automatic** (25% chance, 35% for the 7-card spread) with reversed meanings and
  synthesis lines. There is no user toggle.
- **AI reading exists in code but has never worked in any build.** `callAiReading()` (line ~1931)
  POSTs to `'/api/reading'` — a *relative* URL. `capacitor.config.json` sets no `server.url`, so in
  the APK the WebView origin is `https://localhost` and the request 404s; the catch block logs
  "AI reading failed, using template fallback" and the user gets the template reading with no
  indication, while the Play listing's first bullet is "AI-Powered Readings".
  `api/reading.js` (266 lines, Groq `llama-3.3-70b-versatile`) is real code in the repo, but a live
  probe on 2026-10-27 shows it is **not deployed**: `POST https://tarot-decision-demo.vercel.app/api/reading`
  → `404 NOT_FOUND`, and the `index.html` that deployment actually serves (76,680 bytes) does not
  contain the string `/api/reading` at all. So the endpoint is unshipped, not merely mis-pointed —
  fixing it needs a Vercel deploy plus `GROQ_API_KEY`, not just a URL edit.
- **No notifications** (0 hits for "notif"), **no widgets** (0 hits), **no accounts/cloud sync**
  (localStorage only), **no ad code in HEAD**.
- Card data is fetched from the public `https://tarotapi.dev/api/v1/cards/random` endpoint at draw
  time — so the app is *not* fully offline despite bundled card art.

**Already in the uncommitted working tree (another card's implementation, not shipped yet):**

- `yearly: { id: 'yearly_base' }` — code now matches the Play product id named in the task body,
  with a comment asserting Play's id is `yearly_base` and that `tarot_yearly` silently failed.
  ⚠️ **This is the swap-or-fix risk to verify**: `admob-research.md` §4.3 asserted the opposite
  (Play id = `tarot_yearly`). One of the two is wrong. It is a 30-second check in Play Console →
  Monetize → Products → Subscriptions, and if the code now queries an id Play doesn't have, the bug
  is not fixed — it is inverted, and *monthly* would still work while *yearly* still silently fails.
- `lifetime: { id: 'tarot_lifetime', price: '$19.99' }` (one-time INAPP) and a 3-tier paywall.
- Full AdMob banner integration (`@capacitor-community/admob`, Google **test** ad unit, UMP consent
  flow, banner-inset layout, banner removed when entitlement flips to paid).

**Listing vs reality:** the Play description promises "AI-Powered Readings" (broken in APK) and lists
"Full reading history and journal" and "Outcome tracking" under **PREMIUM** — but both are free and
ungated in code. So the listing overpromises one thing, underpromises another, and misdescribes
where the wall is. Fixing the copy is free and should ride along with the next release.

### 1.4 What each competitor gives away before the wall (the column that matters)

1. **Trusted Tarot (100K+, 4.7★)** — free: *1 daily reading + 1 weekly reading + **unlimited Celtic
   Cross** + one premium reading every 3 days*, plus the full card-meanings library. Paid: "Trusted
   Tarot Unlimited" (1 week / 1 month / 12 month) → 25 premium readings per 24h, daily "Ask a
   Question". Ads on. ([listing](https://play.google.com/store/apps/details?id=com.trustedtarot.app&hl=en&gl=US), [site](https://www.trustedtarot.com/app/)) — *note the free tier includes the same Celtic Cross spread DT chargewalls.*
2. **Tarot – Vázquez (5M+, 4.8★)** — **all 5 spreads, Card of the Day, Yes/No and the 22 Major
   Arcana meanings are free**; the paid version's stated job is "removes ads and allows unlimited
   daily readings". Ads on. This is the highest-install pure-tarot app on Play and it gives the
   product away.
3. **Galaxy Tarot (1M+, 3.5★)** — free app, ads on, "daily card, classic spreads, in-depth guidance"
   free ([site](https://www.galaxytarot.com/)); paid IAP catalog caps at **$14.99**.
4. **Labyrinthos (1M+, 4.8★)** — free readings + free lessons track; premium $9.99/mo ·
   $24.99/qtr · $89.99/yr; à-la-carte reading credits so a light user never subscribes. No ads.
5. **Lumi (1M+, 4.4★)** — free with ads: card of the day, card library, horoscope, moon calendar,
   affirmations; AI readings and AI oracle chat limited on free. No published free-tier numbers.
6. **Tarot AI (100K+, 4.8★)** — "Get your first chat free", then credits/sub. IAP ceiling is only
   $15.99 — an AI-native tarot app still can't sell above ~$16.
7. **TCRM / DEN TSMO (100K+, 4.8★)** — free AI chat first, then gated; 50+ spreads, custom spreads,
   quiz, history. Ads on.
8. **Co–Star (5M+, 4.5★)** — free: daily horoscope, **daily push notifications**, friends/compat,
   birth-chart learning. Paid: full natal chart for non-users, love report, Eros couples. No ads.
   No tarot — included only because its *habit loop* (daily push at a set time) is the closest thing
   to a retention engine in this set.
9. **Tarot! – The Fool's Dog ($3.99 paid, 4.9★, 711 reviews)** — replaced Golden Thread as the
   feature-depth reference: **21 built-in layouts**, free-form layout, "sophisticated journal",
   **reversed-cards toggle**, Major-Arcana-only option, three full books, custom card meanings,
   email-share a reading, no ads, no IAP above $100 (donation-style). This is what "deep" looks like
   at a $3.99 one-time price — and it is 10K+ installs, not 1M+. Depth is not what drives installs.

Scan-only rows (not used in the analysis): **Tarot Simple** `com.myapp.tarotsimple` — 1K+, 4.1★,
27 reviews, $0.99–$59.99, ads; **Golden Thread** `co.threadweave.goldenthread` — now a gratitude app,
100+, no IAP, no ads.

---

## 2. Gap analysis — what competitors give away that DT doesn't

Ranked by "how much of the paywall does this remove for a new user", not by how fun it is to build.
Effort is against **our** codebase: one 2,590-line `public/index.html`, a Capacitor shell, and one
Groq serverless function. That means data/content features are cheap and anything native (widgets,
notifications) pays the Capacitor-plugin tax.

| # | Gap | Who has it | Effort for us | Free or premium? |
|---|---|---|---|---|
| 1 | **Free tier is one reading/day** — the wall arrives before the product does | Every app in §1.4 gives the core reading free (Trusted gives unlimited Celtic Cross; Vázquez gives all 5 spreads) | **Low, 2h** (change two conditions + paywall copy) | **Free** — this is the fix, not a feature |
| 2 | **Working AI reading** (written, wired, never deployed — live endpoint 404s) | Lumi, Tarot AI, TCRM, Co-Star | **Low–Med, 2–4h** (deploy `api/reading.js` to Vercel, set `GROQ_API_KEY`, point the app at an absolute URL, then verify AI text — not the template — on a device) | Free taste / premium depth: 1 AI reading/day free, unlimited premium |
| 3 | **Daily-card habit loop + reminder** | Co–Star (daily push), Galaxy/Trusted (daily card) | **Med, 4–8h** (`@capacitor/local-notifications`, scheduling on daily-card open, Android 13+ `POST_NOTIFICATIONS` permission flow) | Free (it's retention, not revenue) |
| 4 | **Share a reading** (image export) | Tarot! (email share), nobody does it well on Android tarot | **Med, 4–8h** (canvas render of cards + question, then Capacitor Share/Filesystem) | Free — this is the only install-growth feature in the list |
| 5 | **Spread variety** (Yes/No, Love, Career, Situation-Action-Outcome) | Labyrinthos 50+, TCRM 50+, Vázquez 5 incl. Yes/No, Trusted 25 | **Low each, 2–4h per spread** (`SPREADS` entry + position guidance + a layout class) | Mixed: Yes/No + one 3-card variant free; the rest premium |
| 6 | **Reversals toggle** (we randomize with no control) | Tarot! (explicit toggle), Trusted (reversed interpretations) | **Low, 1h** (setting + pass `allowReversed` into the draw) | Free (it's a correctness/trust issue — a user who reads upright-only gets "wrong" cards today) |
| 7 | **Journal search / tags / export** (we store tags but never surface them) | Labyrinthos (notes), Tarot Journal (`com.tarot_journal`, 10K+, free no ads), Tarot! | **Low–Med, 3–5h** (filter over the 50-item localStorage array; export JSON/CSV) | Free (50-reading cap is already a premium-ish limit) |
| 8 | **Outcome reminders** ("you asked this 2 weeks ago — how did it go?") | **Nobody.** Our unique loop, currently passive | **Med, 3–5h** (needs #3 first) | Free — it is the differentiator |
| 9 | **Deck choice** | Labyrinthos, Lumi, Tarot AI, Tarot Journal | **High, 10–20h** (art licensing or generated decks + asset pipeline + build size) | Premium |
| 10 | **Cloud sync / accounts** | Labyrinthos, Co–Star | **High, 20–40h** (Firebase/Supabase + auth UI + Data-safety and privacy-policy rewrite + migration of existing local readings) | Premium |
| 11 | **Home-screen widget** (card of the day) | Nobody in this set (a real gap) | **High, 10–20h** (native AppWidget + Capacitor plugin + WorkManager) | Free (retention) |

**Honest read:** gaps 9–11 are the ones a "we need more features" instinct reaches for, and they are
the three worst-value items on the list. Gaps 1–2 are worth more than 9–11 combined and cost a
day's work between them.

---

## 3. Scenario modeling

Common assumptions (all from `admob-research.md` unless noted):

- Traffic: DT's Play listing shows **10+ installs total**; James's stated run-rate is *single-digit
  installs/day*. Model at **5 installs/day = 150 installs/month** as the optimistic case and note
  that the current *actual* case is ~0/day.
- Subscription conversion: **1–2%** of installs (typical for a no-trial sub in a niche app).
- One-time purchase conversion at a $9.99–$19.99 price point in a mass-market casual app: **0.5–2%**,
  front-loaded by the first-session paywall. I use **1%** as the planning number.
- Banner eCPM **$0.20–$0.80** blended, **$0.50** planning; 2 impressions per DAU-day.
- Play's cut: 15% on the first $1M/yr of revenue → net ≈ 85% of list.

### A. Add more features, keep $9.99/mo + $79.99/yr

To make $9.99/mo defensible you must be comparable to Labyrinthos Premium at the same price: 50+
spreads, journal, lessons/quizzes, multi-deck, sync, no ads. Against our codebase that is roughly
**80–140 hours** (§2 items 5, 7, 9, 10, plus real lesson content — the content is the expensive part,
not the code). Realistic build time for one person on lunch breaks: **3–5 months**.

Revenue at 150 installs/mo and 1–2% conversion: **1.5–3 subscribers/month → $15–$30/mo gross**,
minus the 15% cut → **$13–$26 net**, and only after the 3–5 month build. The yearly SKU keeps
converting at ~0 because $79.99 is 16× the monthly and nobody compares it to Labyrinthos's $89.99.

**Verdict on A: no.** You would spend a quarter of build time to arrive at Labyrinthos's price with
a fraction of its content and 1/100,000th of its installs. Feature parity is a losing race against
apps that have been shipping for 9 years and have a deck business funding them.

### B. Lower the price ($4.99/mo · $24.99–$29.99/yr · $9.99 lifetime)

**Elasticity evidence from the matrix:**

- The one-off ceiling that matters is **$14.99** (Galaxy Tarot's whole catalog, 1M+ installs) and the
  AI-native Tarot AI's range **tops out at $15.99**. The two paid-upfront tarot apps found are
  **Tarot! at $3.99** (10K+, 4.9★) and **Psychic Tarot Oracle at $9.99** (1K+, 5.0★, and it *still*
  shows ads — price and the listing's own structured data, `"price": "9.99"`).
- The only app charging more than $20 as a *subscription* is Labyrinthos — and it has 44.1K reviews,
  50+ spreads, lessons, decks, and no ads. It earns the right to $9.99/mo; we haven't.
- Nothing in the matrix sells a one-time unlock above $14.99. A $19.99+ lifetime is out of market.

**Math at 150 installs/mo, 1% at $9.99 lifetime** = 1.5 × $9.99 = **$15/mo gross, ~$12.7 net**.
At $19.99 it is $30/mo gross *if conversion holds* — but conversion is the thing that falls off a
cliff above ~$15 in this category, so the expected value is roughly the same and the variance is
worse. **$9.99 is the price with the most first sales in it**, and the first sale is the whole
milestone for a $0 app.

**Migration cost: zero.** There are **0 subscribers** today. Repricing a subscription base plan in
Play Console with no active subscribers cannot grandfather anyone and cannot break anyone's billing.
This is the one moment the change is free — after the first customer it stops being free.

**Verdict on B: yes on repricing, but it is not sufficient on its own.** Price was never why the app
earns $0 (see §3.D math). It only decides whether a converted user pays ~$8.50 net or ~$17 net — and
the category data says the cheaper price converts more often than the higher one pays.

### C. Ad banner + a cheap "remove ads" tier ($2.99–$5.99)

**Ads at this traffic are $0.** From the prior doc's table: at 50 DAU a banner earns **~$1.50/mo**;
at 100 DAU **~$3/mo**; AdMob's payout threshold is **$100**, i.e. 3–5 years away at these numbers.
DT currently has 10+ installs total, so DAU is likely single digits — revenue rounds to **$0.00**.

**"Remove ads" at $2.99 is a pricing mistake even so**, for a specific reason: with ads earning
approximately nothing, $2.99 buys the user the *entire product* for less than one month of the old
sub — and it anchors the app's value at $2.99 permanently. But $5.99 for "remove ads only" is also
wrong, because the thing worth paying for is everything, not the absence of a banner nobody minds.

**The one thing ads do well:** they give a one-time unlock a *job*. Galaxy, Trusted, Vázquez,
TCRM and Tarot AI all run banner ads next to a one-time or cheap-sub upgrade; that is the proven
shape in this category, and it is what the prior doc recommended building. Keep it — as a
justification for the unlock, not as a revenue line.

**Verdict on C: yes as the mechanism (banner + one paid unlock), no as a standalone "remove ads"
SKU at $2.99–$5.99.** Price the unlock at $9.99 and let it remove ads *and* unlock the premium
features; the ad becomes the visible nag, the $9.99 becomes the obvious exit.

### D. Hybrid — **this is the recommendation**

**Shape:** free-with-ads, generous core, one $9.99 lifetime unlock as the hero, a $4.99/mo sub for
people who won't pay up front, yearly SKU deactivated.

- **Free:** unlimited 1-card and 3-card readings, 1 Celtic Cross/day, daily card tab, full card
  encyclopedia, full journal + outcome dashboard (all already built — it costs nothing to free it).
  Banner ad on the home/result screen only (never over the cards being read).
- **Premium ($9.99 once, `tarot_lifetime`):** no ads, unlimited everything, AI-written readings
  (the same Groq endpoint, now actually reachable), all future spreads.
- **Monthly $4.99 (`tarot_monthly`):** same entitlements, for the user who won't pay $9.99 up front.
  Payback vs lifetime = 2 months, which is a deliberately short ladder — the sub exists to catch the
  hesitant user, not to be the primary line.
- **Yearly: deactivate.** A yearly priced above ~2× the $9.99 lifetime cannot coexist with it, and a
  yearly priced below 2× lifetime is a worse deal than the monthly for the user and for us.
  Deactivating a base plan with 0 subscribers is free.

**Honest revenue math (150 installs/mo = 5/day):**

| Line | Rate | Monthly gross | Net (85%) |
|---|---|---|---|
| `tarot_lifetime` $9.99 | 1% of 150 = 1.5 sales | $15.00 | $12.75 |
| `tarot_monthly` $4.99 | 0.3% of 150 = 0.45 subs, ~3 mo average life | ~$2.25 | ~$1.91 |
| Banner ads | ~15 DAU × 2 impressions × $0.50 eCPM | ~$0.45 | ~$0.45 |
| **Total** | | **~$17.70** | **~$15.10** |

At the *actual* current traffic (~10 installs ever, not per day) every row is **$0.00** for months.
That is the honest headline: **the monetization design cannot fix a traffic problem.** It can only
stop leaving money on the table once traffic exists.

**What D does to existing subs:** nothing to lose — 0 subscribers. Repricing is free now and gets
expensive later. Migration/policy notes: (1) repricing a base plan in Play Console requires
publishing the change and is irreversible in the sense that you cannot raise it back down without
repeating the process, so set it once; (2) adding `tarot_lifetime` as an **INAPP** product is easy,
but the billing plugin must query `ProductType.INAPP` as well as `SUBS` for `isPremium()` — the
in-flight change already does this, so ship both together or the button errors (§4.4 of the prior
doc); (3) turning ads on requires flipping the "Contains ads" declaration and updating Data safety —
already itemised in `admob-research.md` §3.

---

## 4. The verdict

**Do D, in this order.** Five bullets, then the build list.

1. **Free the core product before touching price.** DT's wall is `1 single-card reading/day`; every
   app with installs gives away the core reading and most spreads (Vázquez, 5M+, gives all five
   spreads away and sells "remove ads + unlimited"). Charging Labyrinthos's $9.99/mo for the
   smallest free tier in the category is why a 4.6★ app with a genuinely novel feature has 10
   installs.
2. **Sell one thing: `tarot_lifetime` at $9.99.** Above ~$16 the category doesn't convert
   (Galaxy's ceiling $14.99; Tarot AI caps at $15.99; paid tarot apps are $3.99 and $9.99).
   Change the in-flight $19.99 to $9.99 **before** it ships — it is one string, and after the first
   customer the price ladder stops being free to change.
3. **Reprice down and kill the yearly.** `tarot_monthly` $9.99 → $4.99, deactivate `yearly_base`
   ($79.99 is 8× the lifetime — incoherent). Zero subscribers means zero migration; this is the only
   time this is true, so do it in the same release.
4. **Ads are a nudge, not income.** Ship the banner already coded, but expect **$0.00–$2/mo** until
   DAU is in the hundreds (AdMob's $100 payout threshold is 3–5 years away at current traffic). Its
   job is to make $9.99 feel like an exit, not to earn.
5. **Then stop doing monetization work and go get installs.** Which competitor feature is most
   likely to move *installs*? Not depth — **shareability and a daily habit**. Tarot! has 21 layouts
   and three books and still only has 10K+ installs; Co–Star has a push notification and has 5M+.
   Our cheapest install levers are (a) **share-a-reading image export** and (b) the **daily-card
   reminder**, plus (c) the one thing nobody else has — **outcome tracking** ("the tarot app that
   keeps score"), which is already built and currently invisible behind a paywall that the listing
   *claims* is a paywall.

### 4.1 SKU sheet implied by the verdict

| SKU | Type | Old | **New** | Grants |
|---|---|---|---|---|
| `tarot_lifetime` | one-time (INAPP, non-consumable) | $19.99 (in flight) | **$9.99** | no ads + all spreads + unlimited + AI readings, forever |
| `tarot_monthly` | auto-renewing sub | $9.99/mo | **$4.99/mo** | same entitlements, monthly |
| `yearly_base` | auto-renewing sub | $79.99/yr | **deactivated** | — |

⚠️ **Verify before changing anything:** the product id that actually exists in Play Console. The
task body says `yearly_base`; the previous research doc says `tarot_yearly`; the in-flight code now
says `yearly_base`. Play product ids are immutable, so exactly one of these is right. Play Console →
Monetize → Products → Subscriptions, 30 seconds, and if the id is wrong the yearly SKU silently
fails forever (the exact bug this card opened with).

### 4.2 Build list, ordered by effort (each item = one small card)

| Order | Work | Effort | Why now |
|---|---|---|---|
| 1 | Deploy `api/reading.js` to Vercel + set `GROQ_API_KEY`; point `callAiReading()` at the absolute URL and verify real AI text (not the template) on a device | 2–4h | The listing already advertises AI and no build has ever delivered it. Highest value-per-hour item in the doc. |
| 2 | Re-cut the free tier: unlimited 1-card + 3-card, 1 Celtic Cross/day; paywall copy that matches reality; **correct the Play listing** (AI works / history+outcome are free) | 2h | Removes the wall that is killing first-run |
| 3 | Reversals toggle in settings | 1h | Trust/correctness: today upright-only readers get reversed cards they didn't ask for |
| 4 | Reprice + deactivate SKUs (Play Console) and set `PRODUCTS` prices in code to match | 1h + review time | Free to do now, not later |
| 5 | Journal search + tags surfacing + JSON export | 3–5h | Makes the unique feature usable; cheap over existing localStorage data |
| 6 | Yes/No spread + one extra 3-card variant (Love, Career) | 2–4h each | Closes the most-requested spread gap cheaply (Vázquez's Yes/No is a top feature) |
| 7 | Daily-card local notification at a user-set time | 4–8h | The habit loop; Co–Star's 5M+ installs run on this |
| 8 | Share-a-reading image export | 4–8h | The only realistic install-growth feature here |
| 9 | Outcome reminder ("how did it turn out?") | 3–5h (after 7) | Turns the unique feature into a retention loop |
| — | Cloud sync, accounts, widgets, multi-deck | 20–40h each | **Do not build yet.** Not a conversion lever at this scale, and each adds privacy/Data-safety surface. |

### 4.3 What would change this recommendation

- **If installs reach ~50/day**, subscriptions start being worth optimising (trials, annual
  emphasis) and cloud sync becomes defensible.
- **If the ad-supported free tier measurably hurts retention** (D1/D7 in Play Console), turn the
  banner off via the existing `ADMOB_IS_TESTING`/config path and keep the $9.99 unlock — the unlock
  does not need the ad to sell once the free tier is good.
- **If a competitor ships outcome tracking**, DT's only real moat is gone and the app becomes a
  worse Labyrinthos. That is the one clock worth watching.

---

## 5. Sources

All Play listings fetched 2026-10-27, `hl=en&gl=US`, with a desktop UA; installs / rating / review
count / IAP range / "Contains ads" read from the page itself. **The machine-readable dump of every
listing behind these tables is committed next to this file as
[`competitive-data-2026-10-27.json`](./competitive-data-2026-10-27.json)** (14 packages, fields +
scrape method), so any number here can be re-checked or re-run later.

- Labyrinthos Tarot Cards — https://play.google.com/store/apps/details?id=com.labyrinthos.app&hl=en&gl=US
- Galaxy Tarot — https://play.google.com/store/apps/details?id=com.hemisphere3.tarot&hl=en&gl=US
- Trusted Tarot — https://play.google.com/store/apps/details?id=com.trustedtarot.app&hl=en&gl=US
- Tarot (Vázquez Software) — https://play.google.com/store/apps/details?id=info.vazquezsoftware.tarot&hl=en&gl=US
- Lumi: AI Tarot & Horoscope — https://play.google.com/store/apps/details?id=com.yobzh.tarot&hl=en&gl=US
- Tarot AI – Card Reading — https://play.google.com/store/apps/details?id=com.tarotreading.aitarot&hl=en&gl=US
- Tarot Card Reading and Meaning (DEN TSMO) — https://play.google.com/store/apps/details?id=com.dt.vfo&hl=en&gl=US
- Co–Star Personalized Astrology — https://play.google.com/store/apps/details?id=com.costarastrology&hl=en&gl=US
- Tarot! (The Fool's Dog, $3.99) — https://play.google.com/store/apps/details?id=com.foolsdog.classic1910&hl=en&gl=US
- Tarot Journal — https://play.google.com/store/apps/details?id=com.tarot_journal&hl=en&gl=US
- Tarot Simple — https://play.google.com/store/apps/details?id=com.myapp.tarotsimple&hl=en&gl=US
- Golden Thread (now a gratitude app) — https://play.google.com/store/apps/details?id=co.threadweave.goldenthread&hl=en&gl=US
- Decision Tarot (ours) — https://play.google.com/store/apps/details?id=com.keystonedigital.decisiontarot&hl=en&gl=US
- Galaxy Tarot site — https://www.galaxytarot.com/
- Trusted Tarot free-tier FAQ — https://www.trustedtarot.com/app/
- Labyrinthos site/pricing — https://labyrinthos.co/ ; tier prices from `admob-research.md` §4
  (https://www.tarotalmanac.com/vs/labyrinthos, https://appstoreprice.org/en/apps/1155180220)
- eCPM + revenue-per-DAU benchmarks, Play policy, plugin choice — `docs/admob-research.md` §2–§5

Repo evidence (read-only):
- `public/index.html` @ HEAD `8064098` — spread gate (`selectSpread`, `num > 1`), daily gate
  (`drawCards` → `getDailyDrawCount() >= 1`), `saveReading`/`loadDashboard`, reversal randomization,
  `callAiReading()` relative fetch, no notification/widget/account code
- `public/index.html` working tree — `yearly_base`, `tarot_lifetime` $19.99, AdMob banner (test ids),
  3-tier paywall
- `api/reading.js` — Groq `llama-3.3-70b-versatile`, retries, template fallback
- `capacitor.config.json` — no `server.url`, so the WebView origin is local (`/api/*` cannot resolve)

## 6. What I did NOT verify

- **No Play Console access.** Product ids, subscriber counts (assumed 0 from "app earns $0"), and
  current prices are from James's card body + repo, not from the dashboard. The `yearly_base` vs
  `tarot_yearly` conflict is unresolved by design — it needs one look at the console.
- **Free-tier limits for Labyrinthos, Lumi, Tarot AI and TCRM are not published.** Where a listing
  said "first chat free" I quoted that; where it said nothing I wrote `?` rather than guessing. The
  exact number of free spreads in each app is only visible inside its paywall.
- **Individual SKU prices are mostly not public.** Play publishes ranges; the ranges are tabulated.
  Co–Star's monthly price was not verified in this pass (prior doc flagged the same).
- **Revenue is modelled, not observed.** No public source gives ARPU for these apps; installs and
  review counts are the only scale proxies.
- **The Vercel probe covered one host.** `https://tarot-decision-demo.vercel.app` (the project named
  in `.vercel/project.json`) answers 200 at `/` and 404 at `/api/*`, and the `/` HTML it serves is
  older than the repo. If a custom domain or a second project deploys the same app, I did not find
  it — but the Play build cannot reach either one as written, so the conclusion stands for the APK.
- **No A/B evidence for the $9.99 vs $19.99 lifetime choice** — it is inferred from the category
  ceiling (Galaxy $14.99, Tarot AI $15.99, paid tarot $3.99/$9.99), not measured on DT.
- **Golden Thread Tarot's delisting is inferred** from the package now hosting a different app plus
  Play search returning only that app. It may have moved to a new package id I did not find.
