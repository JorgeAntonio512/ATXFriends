# Adaptive Sizing Reference Guide

## Quick Reference: Size Calculations for Different iPhones

### Screen Dimensions
```
iPhone SE (3rd gen):    375pt wide × 667pt tall
iPhone 14/15:           390pt wide × 844pt tall
iPhone 14/15 Pro Max:   430pt wide × 932pt tall
iPhone 16 Pro Max:      430pt wide × 932pt tall
```

---

## Spacing Formulas

### Top Spacing (After Safe Area)
```swift
// Formula: max(geometry.size.height * percentage, minimum)

// Small spacing (1.5-2%):
max(geometry.size.height * 0.02, 8)
// iPhone SE:  max(667 * 0.02, 8)  = max(13.3, 8)  = 13pt
// iPhone 14:  max(844 * 0.02, 8)  = max(16.9, 8)  = 17pt
// Pro Max:    max(932 * 0.02, 8)  = max(18.6, 8)  = 19pt

// Medium spacing (2.5-3%):
max(geometry.size.height * 0.03, 16)
// iPhone SE:  max(667 * 0.03, 16) = max(20.0, 16) = 20pt
// iPhone 14:  max(844 * 0.03, 16) = max(25.3, 16) = 25pt
// Pro Max:    max(932 * 0.03, 16) = max(28.0, 16) = 28pt

// Large spacing (4%):
max(geometry.size.height * 0.04, 20)
// iPhone SE:  max(667 * 0.04, 20) = max(26.7, 20) = 27pt
// iPhone 14:  max(844 * 0.04, 20) = max(33.8, 20) = 34pt
// Pro Max:    max(932 * 0.04, 20) = max(37.3, 20) = 37pt
```

### Bottom Spacing
```swift
// Formula: Same as top, typically 2-3%

max(geometry.size.height * 0.02, 16)
// iPhone SE:  max(667 * 0.02, 16) = max(13.3, 16) = 16pt
// iPhone 14:  max(844 * 0.02, 16) = max(16.9, 16) = 17pt
// Pro Max:    max(932 * 0.02, 16) = max(18.6, 16) = 19pt
```

---

## Font Size Formulas

### Large Titles
```swift
// Formula: min(targetSize, geometry.size.width * percentage)

min(42, geometry.size.width * 0.112)
// iPhone SE:  min(42, 375 * 0.112) = min(42, 42.0) = 42pt  ✓ Maximum
// iPhone 14:  min(42, 390 * 0.112) = min(42, 43.7) = 42pt  ✓ Maximum
// Pro Max:    min(42, 430 * 0.112) = min(42, 48.2) = 42pt  ✓ Maximum
```

### Section Titles
```swift
min(32, geometry.size.width * 0.085)
// iPhone SE:  min(32, 375 * 0.085) = min(32, 31.9) = 32pt  ~ Almost max
// iPhone 14:  min(32, 390 * 0.085) = min(32, 33.2) = 32pt  ✓ Maximum
// Pro Max:    min(32, 430 * 0.085) = min(32, 36.6) = 32pt  ✓ Maximum
```

### Standard Titles
```swift
min(28, geometry.size.width * 0.075)
// iPhone SE:  min(28, 375 * 0.075) = min(28, 28.1) = 28pt  ✓ At max
// iPhone 14:  min(28, 390 * 0.075) = min(28, 29.3) = 28pt  ✓ Maximum
// Pro Max:    min(28, 430 * 0.075) = min(28, 32.3) = 28pt  ✓ Maximum
```

### Body Text / Subtitles
```swift
min(16, geometry.size.width * 0.043)
// iPhone SE:  min(16, 375 * 0.043) = min(16, 16.1) = 16pt  ✓ At max
// iPhone 14:  min(16, 390 * 0.043) = min(16, 16.8) = 16pt  ✓ Maximum
// Pro Max:    min(16, 430 * 0.043) = min(16, 18.5) = 16pt  ✓ Maximum
```

---

## Icon & Image Sizes

### Large Logo (OnboardingView)
```swift
// Formula: min(maxSize, geometry.size.width * percentage)

min(100, geometry.size.width * 0.27)
// iPhone SE:  min(100, 375 * 0.27) = min(100, 101.3) = 100pt ✓ At max
// iPhone 14:  min(100, 390 * 0.27) = min(100, 105.3) = 100pt ✓ Maximum
// Pro Max:    min(100, 430 * 0.27) = min(100, 116.1) = 100pt ✓ Maximum

// Icon inside logo:
min(42, geometry.size.width * 0.112)
// iPhone SE:  min(42, 375 * 0.112) = min(42, 42.0)  = 42pt  ✓ At max
// iPhone 14:  min(42, 390 * 0.112) = min(42, 43.7)  = 42pt  ✓ Maximum
// Pro Max:    min(42, 430 * 0.112) = min(42, 48.2)  = 42pt  ✓ Maximum
```

### Profile Circle (ProfileSetupFlowView)
```swift
min(140, geometry.size.width * 0.37)
// iPhone SE:  min(140, 375 * 0.37) = min(140, 138.8) = 139pt ~ Almost max
// iPhone 14:  min(140, 390 * 0.37) = min(140, 144.3) = 140pt ✓ Maximum
// Pro Max:    min(140, 430 * 0.37) = min(140, 159.1) = 140pt ✓ Maximum

// Icon inside circle:
min(80, geometry.size.width * 0.21)
// iPhone SE:  min(80, 375 * 0.21) = min(80, 78.8)  = 79pt  ~ Almost max
// iPhone 14:  min(80, 390 * 0.21) = min(80, 81.9)  = 80pt  ✓ Maximum
// Pro Max:    min(80, 430 * 0.21) = min(80, 90.3)  = 80pt  ✓ Maximum
```

### Small Icons (ForgotPasswordSheet)
```swift
min(80, geometry.size.width * 0.21)
// iPhone SE:  min(80, 375 * 0.21) = min(80, 78.8)  = 79pt
// iPhone 14:  min(80, 390 * 0.21) = min(80, 81.9)  = 80pt  ✓ Maximum
// Pro Max:    min(80, 430 * 0.21) = min(80, 90.3)  = 80pt  ✓ Maximum
```

---

## Conditional Spacing

### Based on Screen Height
```swift
// Tall screens (> 700pt) get more generous spacing
geometry.size.height > 700 ? 16 : 12

// iPhone SE (667pt):   12pt  (compact)
// iPhone 14 (844pt):   16pt  (generous)
// Pro Max (932pt):     16pt  (generous)
```

### VStack Spacing Examples
```swift
// Forms and cards:
VStack(spacing: geometry.size.height > 700 ? 16 : 12)

// Button groups:
VStack(spacing: geometry.size.height > 700 ? 12 : 8)

// Divider sections:
.padding(.vertical, geometry.size.height > 700 ? 8 : 4)
```

---

## Real-World Examples

### OnboardingView Logo Breakdown

**iPhone SE (375 × 667):**
```
Circle:     100pt × 100pt  (27% of 375 = 101pt, capped at 100)
Icon:       42pt           (11.2% of 375 = 42pt, exactly at max)
App Name:   42pt font      (same calculation)
Top Space:  27pt           (4% of 667 = 27pt)
Bottom Pad: 20pt           (3% of 667 = 20pt)
```

**iPhone 14 (390 × 844):**
```
Circle:     100pt × 100pt  (would be 105pt, capped at 100)
Icon:       42pt           (would be 44pt, capped at 42)
App Name:   42pt font      (would be 44pt, capped at 42)
Top Space:  34pt           (4% of 844 = 34pt)
Bottom Pad: 25pt           (3% of 844 = 25pt)
```

**iPhone 16 Pro Max (430 × 932):**
```
Circle:     100pt × 100pt  (would be 116pt, capped at 100)
Icon:       42pt           (would be 48pt, capped at 42)
App Name:   42pt font      (would be 48pt, capped at 42)
Top Space:  37pt           (4% of 932 = 37pt)
Bottom Pad: 28pt           (3% of 932 = 28pt)
```

### SignInView Header Breakdown

**iPhone SE (375 × 667):**
```
Top Space:        13pt  (2% of 667 = 13pt)
Title Font:       28pt  (7.5% of 375 = 28pt, at max)
Subtitle Font:    16pt  (4.3% of 375 = 16pt, at max)
Header Bottom:    17pt  (2.5% of 667 = 17pt)
Form VStack:      12pt  (height < 700, uses compact spacing)
```

**iPhone 14 (390 × 844):**
```
Top Space:        17pt  (2% of 844 = 17pt)
Title Font:       28pt  (would be 29pt, capped at 28)
Subtitle Font:    16pt  (would be 17pt, capped at 16)
Header Bottom:    21pt  (2.5% of 844 = 21pt)
Form VStack:      16pt  (height > 700, uses generous spacing)
```

**iPhone 16 Pro Max (430 × 932):**
```
Top Space:        19pt  (2% of 932 = 19pt)
Title Font:       28pt  (would be 32pt, capped at 28)
Subtitle Font:    16pt  (would be 19pt, capped at 16)
Header Bottom:    23pt  (2.5% of 932 = 23pt)
Form VStack:      16pt  (height > 700, uses generous spacing)
```

---

## Choosing the Right Percentage

### Guidelines for Different Element Types:

#### Vertical Spacing (Height-Based)
```
Tiny gaps:        1.5%  (min 8pt)   → 10-14pt range
Small spacing:    2%    (min 8pt)   → 13-19pt range
Medium spacing:   2.5%  (min 16pt)  → 17-23pt range
Large spacing:    3%    (min 16pt)  → 20-28pt range
Extra large:      4%    (min 20pt)  → 27-37pt range
```

#### Font Sizes (Width-Based)
```
Caption:          3.2%  (min 12pt, max 12pt)
Body:             4.0%  (min 14pt, max 16pt)
Subheadline:      4.3%  (min 15pt, max 17pt)
Headline:         6.4%  (min 20pt, max 24pt)
Title:            7.5%  (min 24pt, max 28pt)
Large Title:      8.5%  (min 28pt, max 32pt)
Hero Title:       11.2% (min 36pt, max 42pt)
```

#### Icons & Images (Width-Based)
```
Small icon:       13%   (max 50pt)   → 49-56pt range
Medium icon:      21%   (max 80pt)   → 79-90pt range
Large icon:       27%   (max 100pt)  → 101-116pt range
Hero icon:        37%   (max 140pt)  → 139-159pt range
```

---

## Common Patterns

### Pattern 1: Constrained Title
```swift
Text("Welcome")
    .font(.system(
        size: min(32, geometry.size.width * 0.085),
        weight: .bold,
        design: .rounded
    ))
```
**Why:** Prevents titles from becoming huge on large screens, while scaling down proportionally on small screens.

### Pattern 2: Adaptive Icon
```swift
Image(systemName: "star.fill")
    .font(.system(size: min(50, geometry.size.width * 0.13)))
    .frame(
        width: min(100, geometry.size.width * 0.27),
        height: min(100, geometry.size.width * 0.27)
    )
```
**Why:** Icon size scales with frame, maintaining consistent proportions across all devices.

### Pattern 3: Flexible Spacer
```swift
Spacer()
    .frame(height: max(geometry.size.height * 0.03, 16))
```
**Why:** Grows on larger screens, maintains minimum spacing on small screens.

### Pattern 4: Conditional Layout
```swift
VStack(spacing: geometry.size.height > 700 ? 16 : 12) {
    // Content that needs tighter spacing on small screens
}
```
**Why:** Two distinct layout modes: compact (iPhone SE, Mini) and generous (standard & Pro).

---

## Testing Checklist

### Visual Tests by Device:

#### iPhone SE (Minimum Size)
- [ ] No content clipped at top
- [ ] All buttons visible and tappable
- [ ] Fonts readable (not too small)
- [ ] Icons not tiny (<40pt)
- [ ] Spacing not cramped (<8pt gaps)
- [ ] ScrollView allows access to all content

#### iPhone 14/15 (Standard Size)
- [ ] Balanced spacing (neither cramped nor excessive)
- [ ] Fonts at optimal reading size
- [ ] Icons prominent but not oversized
- [ ] Layout feels centered and intentional

#### iPhone 16 Pro Max (Maximum Size)
- [ ] No excessive whitespace
- [ ] Fonts capped at readable max size
- [ ] Icons capped (not cartoonishly large)
- [ ] Content still feels cohesive
- [ ] Extra space used for padding, not element size

### Mathematical Verification:
```swift
// For any adaptive size formula, verify:
1. Minimum value is reasonable (e.g., 8pt spacing minimum)
2. Maximum value is capped where appropriate
3. Percentage yields expected results on SE
4. Result on Pro Max is intentional
```

---

## Quick Reference Table

| Element Type | Formula | iPhone SE | iPhone 14 | Pro Max |
|-------------|---------|-----------|-----------|---------|
| **Spacing** | | | | |
| Tiny gap | `max(h * 0.015, 8)` | 10pt | 13pt | 14pt |
| Small space | `max(h * 0.02, 8)` | 13pt | 17pt | 19pt |
| Medium space | `max(h * 0.025, 16)` | 17pt | 21pt | 23pt |
| Large space | `max(h * 0.03, 16)` | 20pt | 25pt | 28pt |
| XL space | `max(h * 0.04, 20)` | 27pt | 34pt | 37pt |
| **Fonts** | | | | |
| Body | `min(16, w * 0.043)` | 16pt | 16pt | 16pt |
| Title | `min(28, w * 0.075)` | 28pt | 28pt | 28pt |
| Section | `min(32, w * 0.085)` | 32pt | 32pt | 32pt |
| Hero | `min(42, w * 0.112)` | 42pt | 42pt | 42pt |
| **Icons** | | | | |
| Small icon | `min(50, w * 0.13)` | 49pt | 50pt | 50pt |
| Medium icon | `min(80, w * 0.21)` | 79pt | 80pt | 80pt |
| Large icon | `min(100, w * 0.27)` | 101pt | 100pt | 100pt |
| XL icon | `min(140, w * 0.37)` | 139pt | 140pt | 140pt |

**Legend:**
- `h` = `geometry.size.height`
- `w` = `geometry.size.width`

---

## Best Practices Summary

1. **Always use `min()` for font sizes** - prevents oversized text on large screens
2. **Always use `max()` for spacing** - ensures minimum spacing on small screens
3. **Base spacing on height** - vertical rhythm depends on screen height
4. **Base fonts on width** - readability depends on line length
5. **Base icons on width** - visual balance with surrounding text
6. **Test on SE first** - if it works on smallest screen, it works everywhere
7. **Use percentages for proportions** - absolute values for caps/minimums
8. **Add ScrollView for keyboard** - prevents content clipping on all screens

---

**Last Updated:** May 25, 2026  
**Devices Tested:** iPhone SE (3rd gen), iPhone 14, iPhone 16 Pro Max  
**SwiftUI Version:** iOS 17+
