//
//  GeoHashUtility.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import Foundation

/// Utility for encoding geographic coordinates into geohash strings
/// Used for proximity-based querying of groups
struct GeoHashUtility {
    /// Encodes latitude and longitude into a geohash string
    /// - Parameters:
    ///   - latitude: The latitude coordinate
    ///   - longitude: The longitude coordinate
    ///   - precision: The number of characters in the hash (default 5 ≈ 5km precision)
    /// - Returns: A geohash string of the specified precision
    static func encode(latitude: Double, longitude: Double, precision: Int = 5) -> String {
        let base32 = Array("0123456789bcdefghjkmnpqrstuvwxyz")
        var minLat = -90.0, maxLat = 90.0
        var minLon = -180.0, maxLon = 180.0
        var hash = ""
        var bit = 0
        var ch = 0
        var isEven = true

        while hash.count < precision {
            if isEven {
                let mid = (minLon + maxLon) / 2
                if longitude > mid {
                    ch |= (1 << (4 - bit))
                    minLon = mid
                } else {
                    maxLon = mid
                }
            } else {
                let mid = (minLat + maxLat) / 2
                if latitude > mid {
                    ch |= (1 << (4 - bit))
                    minLat = mid
                } else {
                    maxLat = mid
                }
            }
            isEven.toggle()
            if bit < 4 {
                bit += 1
            } else {
                hash.append(base32[ch])
                bit = 0
                ch = 0
            }
        }
        return hash
    }
}
