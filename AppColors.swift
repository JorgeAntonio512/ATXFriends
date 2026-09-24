//  AppColors.swift
//  Avenue3

import SwiftUI

extension Color {
    /// #BF5700 UT Burnt Orange — primary brand color for tints, icons, and interactive elements.
    /// Unchanged between Light/Dark by design — reads fine on both.
    static let appPrimary = Color(red: 0.74902, green: 0.34118, blue: 0.0)
    /// #1B2A47 UT Deep Navy — for solid-fill buttons (e.g. Yay/Connect) with white text on top.
    /// Unchanged between Light/Dark by design — it's a filled shape, not text on a surface.
    /// Do NOT use this for text color; use `appPrimaryText` instead so it adapts.
    static let appNavy = Color(red: 0.10588, green: 0.16471, blue: 0.27843)

    // MARK: - Adaptive semantic colors (Assets.xcassets, Light/Dark variants)
    //
    // `appBackground` itself is NOT declared here — Xcode auto-generates it from the
    // "AppBackground" colorset (asset symbol extensions), and a manual redeclaration
    // here is a build error. `Color.appBackground` resolves to that generated symbol.

    /// Card/section surface, sits on top of `appBackground`. Light: white. Dark: elevated charcoal.
    static let appCardBackground = Color("CardBackground")
    /// Primary headline/label text. Light: UT Navy. Dark: warm off-white.
    static let appPrimaryText = Color("PrimaryText")
    /// Secondary/caption/subtitle text. Light: mid-gray. Dark: warm light gray.
    static let appSecondaryText = Color("SecondaryText")
    /// Dividers, strokes, unselected borders. Also doubles as a neutral placeholder-fill gray.
    static let appBorder = Color("Border")
    /// Destructive actions (Nay, Sign Out, delete). Consolidates the app's several ad-hoc reds
    /// that all resolved to the same RGB value (0.85, 0.45, 0.40).
    static let appDanger = Color("Danger")

    // MARK: - Text gray scale (Assets.xcassets, Light/Dark variants)
    //
    // The app used ~10 distinct hand-picked grays for body/caption/placeholder text instead of
    // a shared scale. Each one gets its own token so its Light value is pixel-identical to
    // today, with a Dark counterpart on the same emphasis curve as PrimaryText/SecondaryText
    // (darker-in-Light == brighter-in-Dark).
    static let appTextHeavy = Color("TextHeavy")         // 0.25 gray
    static let appTextStrong = Color("TextStrong")       // 0.35 gray
    static let appTextBody = Color("TextBody")           // 0.40 gray
    static let appTextMedium = Color("TextMedium")       // 0.45 gray
    // 0.50 gray is `appSecondaryText` above — exact match, reused as-is.
    static let appTextTertiary = Color("TextTertiary")   // 0.55 gray
    static let appTextMuted = Color("TextMuted")         // 0.60 gray
    static let appTextFaint = Color("TextFaint")         // 0.65 gray
    static let appTextSubtle = Color("TextSubtle")       // 0.70 gray

    // MARK: - Status/badge accents — fixed (unchanged Light/Dark by design)
    //
    // Same rationale as appPrimary/appNavy above: these are saturated fills or text sitting on
    // top of a card surface, in small doses (icons, tags, pill text) — they read fine on both an
    // off-white and an elevated-charcoal card. Flagged in the Step 3 report for a second look.
    static let appPositiveGreen = Color(red: 0.30, green: 0.58, blue: 0.35)
    static let appPositiveGreenTint = Color(red: 0.55, green: 0.80, blue: 0.55)
    static let appDeclinedRed = Color(red: 0.72, green: 0.33, blue: 0.28)
    static let appCancelledText = Color(red: 0.60, green: 0.35, blue: 0.30)
    static let appCancelledTint = Color(red: 0.80, green: 0.40, blue: 0.35)
    static let appCounterText = Color(red: 0.40, green: 0.40, blue: 0.70)
    static let appCounterTint = Color(red: 0.50, green: 0.50, blue: 0.85)
    static let appPendingGold = Color(red: 0.70, green: 0.55, blue: 0.20)
    static let appPendingGoldTint = Color(red: 0.97, green: 0.90, blue: 0.60)
    static let appPromptGold = Color(red: 0.60, green: 0.45, blue: 0.20)
    static let appPromptGoldAccent = Color(red: 0.72, green: 0.55, blue: 0.25)
    static let appIconWarm = Color(red: 0.85, green: 0.55, blue: 0.40)
    static let appIconInfo = Color(red: 0.45, green: 0.60, blue: 0.70)
    static let appIconSafe = Color(red: 0.45, green: 0.70, blue: 0.45)
    static let appDangerStrong = Color(red: 0.90, green: 0.35, blue: 0.35)
    static let appAmber = Color(red: 0.85, green: 0.65, blue: 0.30)
    static let appDenyRed = Color(red: 0.75, green: 0.35, blue: 0.35)
    /// A dark, opaque "toast" bezel (e.g. a "Creating group…" overlay) with white text on top —
    /// unlike `appTextStrong`, this is a background fill, not text, so it must NOT get brighter
    /// in Dark Mode. Fixed by design, same rationale as appPrimary/appNavy above.
    static let appToastBackground = Color(red: 0.35, green: 0.35, blue: 0.35)

    // MARK: - Opaque status-card backgrounds (Assets.xcassets, Light/Dark variants)
    //
    // Unlike the tints above, these fully cover the card behind them (no opacity), so — unlike
    // the small accents — they need a real Dark variant or they'd sit as a bright island on a
    // dark screen.
    static let appPositiveCardBg = Color("PositiveCardBg")
    static let appPromptCreamBg = Color("PromptCreamBg")

    // MARK: - Apple / Google Sign-In buttons
    //
    // Both rows use the same white/black neutral button style. Kept fixed rather than adapted —
    // Apple's own "Sign in with Apple" guidelines and Google's brand guidelines both specify this
    // as a valid, self-contained button style independent of the host app's theme.
    static let socialButtonBackground = Color.white
    static let socialButtonBorder = Color.black
    static let socialButtonText = Color.black
    static let googleLogoBlue = Color(red: 0.26, green: 0.52, blue: 0.96)
    static let googleLogoRed = Color(red: 0.92, green: 0.25, blue: 0.21)
}
