# Tarot Decision App - TODO List

**Goal:** Ship to Google Play Store as one-time $6.99 purchase

---

## 🎨 P0 - Must Have (Shippable Product)

### 1. Card Images ✅ COMPLETE (2026-03-28)
- [x] Download public domain Rider-Waite-Smith images (78 cards)
- [x] Host images (Vercel static assets or CDN)
- [x] Wire up images to card display in UI
- [ ] Add card back image for face-down state
- [ ] Test on mobile (responsive sizing)

### 2. Mobile App Build
- [ ] Initialize Capacitor project
- [ ] Configure Android (target SDK 35, package name)
- [ ] Configure iOS (if doing iOS later)
- [ ] Sync web assets to native project
- [ ] Build APK for testing
- [ ] Build AAB for Play Store

### 3. Payment Integration
- [ ] Set up RevenueCat (or Stripe)
- [ ] Create $6.99 one-time purchase product
- [ ] Add paywall UI (unlock full readings)
- [ ] Implement purchase flow
- [ ] Test on Android (license testing)
- [ ] Configure Play Console in-app products

### 4. Reading Quality
- [ ] Write better card interpretation templates
- [ ] Add position-specific meanings (Situation/Choice/Outcome)
- [ ] Include reversals (optional, but adds depth)
- [ ] Test readings feel personal, not generic

---

## 🚀 P1 - Should Have (Better UX)

### 5. UI Polish
- [ ] Add card flip animation
- [ ] Smooth transitions between screens
- [ ] Loading states while drawing cards
- [ ] Better typography and spacing
- [ ] Dark mode (already have dark theme, verify)

### 6. App Structure
- [ ] Add onboarding screen (what is this app?)
- [ ] Add settings screen (toggle reversals, sound, etc.)
- [ ] Add saved readings history
- [ ] Add share reading feature (image/text)

### 7. Sound & Haptics
- [ ] Add card shuffle sound
- [ ] Add card flip sound
- [ ] Haptic feedback on draw
- [ ] Background ambience (optional)

---

## 🌟 P2 - Nice to Have (Post-Launch)

### 8. AI-Powered Readings
- [ ] Integrate AI for personalized interpretations
- [ ] Prompt template for decision-focused readings
- [ ] Context from user's question + card combo
- [ ] Rate limit / credit system if using paid API

### 9. More Spread Types
- [ ] Single card (daily draw)
- [ ] Celtic Cross (advanced users)
- [ ] Relationship spread (2-person dynamics)
- [ ] Custom spreads

### 10. Social Features
- [ ] Share reading as image
- [ ] Export reading to PDF
- [ ] Daily reminder notifications
- [ ] Reading journal with notes

---

## 📦 Launch Checklist

### Pre-Launch
- [ ] App icon (512x512, adaptive icon)
- [ ] Feature graphic (1024x500)
- [ ] Screenshots (phone + tablet)
- [ ] Privacy policy (generate from template)
- [ ] Terms of service
- [ ] App description for Play Store
- [ ] Content rating questionnaire

### Play Store Submission
- [ ] Create app listing
- [ ] Upload AAB
- [ ] Set pricing ($6.99 one-time)
- [ ] Submit for review (2-7 days)
- [ ] Monitor review status

### Post-Launch
- [ ] Monitor crash reports
- [ ] Track conversion (free → paid)
- [ ] Collect user reviews
- [ ] Plan updates based on feedback

---

## 📅 Timeline Estimate

| Phase | Tasks | Time |
|-------|-------|------|
| **Week 1** | Card images, UI polish, Capacitor setup | 3-4 days |
| **Week 2** | Payment integration, testing, fixes | 3-4 days |
| **Week 3** | Play Store assets, submission, review | 5-7 days (includes review wait) |

**Target Launch:** Mid-April 2026

---

## 📝 Notes

- **Differentiator:** Decision-focused, not generic daily readings
- **Pricing:** $6.99 one-time (undercut subscription apps)
- **Target:** People at crossroads (career, relationships, life decisions)
- **Marketing:** TikTok/Instagram reels showing readings (viral potential)

---

*Last updated: 2026-03-28*
