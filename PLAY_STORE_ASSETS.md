# Play Store Assets Needed

## App Icon (Required)

**Sizes needed:**
- `mipmap-mdpi/ic_launcher.png` - 48x48px
- `mipmap-hdpi/ic_launcher.png` - 72x72px
- `mipmap-xhdpi/ic_launcher.png` - 96x96px
- `mipmap-xxhdpi/ic_launcher.png` - 144x144px
- `mipmap-xxxhdpi/ic_launcher.png` - 192x192px
- `mipmap-mdpi/ic_launcher_foreground.png` - 108x108px (foreground only)
- `mipmap-hdpi/ic_launcher_foreground.png` - 162x162px
- `mipmap-xhdpi/ic_launcher_foreground.png` - 216x216px
- `mipmap-xxhdpi/ic_launcher_foreground.png` - 324x324px
- `mipmap-xxxhdpi/ic_launcher_foreground.png` - 432x432px

**Adaptive Icon requirements:**
- Foreground: centered icon with transparent background
- Background: solid color or simple pattern (defined in `drawable/ic_launcher_background.xml`)

**Design suggestions:**
- Gold star/moon symbol on dark navy background (#0f0c29)
- Or: Single tarot card silhouette with gold accents
- Or: Crystal ball with mystical glow

---

## Feature Graphic (Required for Play Store)

**Size:** 1024x500px

**Content ideas:**
- Dark mystical background matching app theme (#0f0c29)
- Gold accent color (#d4af37)
- App name "Decision Tarot" in Cinzel font
- Tagline: "Find clarity in your decisions"
- Tarot card imagery or mystical symbols

---

## Screenshots (Required for Play Store)

**Need:**
- 2-8 screenshots per device type
- Phone: 16:9 or 18:9 aspect ratio
- Tablet: 16:10 or 4:3 aspect ratio

**Screenshot ideas:**
1. Home screen showing spread selection
2. Card reveal animation (mid-flip)
3. Reading interpretation result
4. Card encyclopedia browsing
5. Reading history view

**How to capture:**
- Use Android emulator (already set up: Pixel 6 API 34)
- Take screenshots via Android Studio or `adb exec-out screencap -p > screenshot.png`

---

## Privacy Policy

✅ Created at `/public/privacy-policy.html`

---

## Quick Commands

### Generate adaptive icons (if you have an SVG)
```bash
# Using ImageMagick (install first)
convert icon-foreground.svg -resize 432x432 mipmap-xxxhdpi/ic_launcher_foreground.png
# Repeat for each size
```

### Capture screenshots from emulator
```bash
# Start emulator first
adb devices
adb exec-out screencap -p > screenshot-1.png
```

### Build release AAB
```bash
cd android
./gradlew bundleRelease
# Output: android/app/build/outputs/bundle/release/app-release.aab
```