//
//  DistanceDisplay.swift
//  Avenue3
//
//  Single source of truth for how a distance to another user is shown. Always
//  bucketed, never an exact number, and nil whenever that user hasn't opted
//  in to sharing their location — the caller shows no distance line at all.
//

import Foundation

enum DistanceDisplay {
    /// Returns the display label for a distance, or nil if it shouldn't be
    /// shown (the other user isn't sharing their location, or miles is nil).
    static func label(miles: Double?, isSharing: Bool) -> String? {
        guard isSharing, let miles else { return nil }
        return bucketLabel(miles: miles)
    }

    /// Buckets a raw mile count into one of the fixed display labels — the
    /// exact distance is never shown, only which bucket it falls into.
    static func bucketLabel(miles: Double) -> String {
        switch miles {
        case ..<1: return "Under 1 mi away"
        case ..<3: return "~2 mi away"
        case ..<7: return "~5 mi away"
        case ..<12: return "~10 mi away"
        case ..<20: return "~15 mi away"
        case ..<35: return "~25 mi away"
        case ..<60: return "~50 mi away"
        default: return "50+ mi away"
        }
    }
}
