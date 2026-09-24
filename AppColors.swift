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
    /// Dividers, strokes, unselected borders.
    static let appBorder = Color("Border")
    /// Destructive actions (Nay, Sign Out, delete). Consolidates the app's several ad-hoc reds.
    static let appDanger = Color("Danger")
}
