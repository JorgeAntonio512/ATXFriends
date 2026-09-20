//
//  HomeViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import CoreLocation
import SwiftUI

/// ViewModel for home/discovery tab
@Observable
final class HomeViewModel: NSObject, CLLocationManagerDelegate {
    // MARK: - Published State
    
    /// Current user (Firebase representation)
    var currentUser: FirebaseUser?
    
    /// Nearby users within radius (Firebase representation)
    var nearbyUsers: [FirebaseUser] = []
    
    /// Selected search radius
    var selectedRadius: Double = 10.0
    
    /// Error message to display
    var errorMessage: String?
    
    /// Whether data is loading
    var isLoading: Bool = false
    
    // MARK: - Services
    
    private let firestoreService = FirestoreService.shared
    private let authService = FirebaseAuthService.shared
    
    // MARK: - Location Manager
    
    private let locationManager = CLLocationManager()
    private var userLocation: CLLocationCoordinate2D?
    
    // MARK: - Initialization
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
    }
    
    // MARK: - Location Permission
    
    /// Returns the current location authorization status
    var locationAuthorizationStatus: CLAuthorizationStatus {
        return locationManager.authorizationStatus
    }
    
    /// Checks if location permission has been granted
    var hasLocationPermission: Bool {
        let status = locationManager.authorizationStatus
        return status == .authorizedWhenInUse || status == .authorizedAlways
    }
    
    /// Requests location permission - should only be called when user explicitly taps "Enable Location"
    @MainActor
    func requestLocationPermission() {
        let status = locationManager.authorizationStatus
        
        switch status {
        case .notDetermined:
            // This will trigger the system permission dialog
            locationManager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            // Already authorized, start updating
            locationManager.startUpdatingLocation()
        case .denied, .restricted:
            // Permission denied - app should show settings prompt
            break
        @unknown default:
            break
        }
    }
    
    /// Uses a default location (Austin, TX) without requesting permission
    @MainActor
    func useDefaultLocation() {
        userLocation = CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431)
    }
    
    // MARK: - CLLocationManagerDelegate
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // When authorization changes, start location updates if authorized
        let status = manager.authorizationStatus
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            manager.startUpdatingLocation()
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        userLocation = location.coordinate
        
        // Update current user's location
        Task { @MainActor in
            if var user = currentUser {
                user.latitude = location.coordinate.latitude
                user.longitude = location.coordinate.longitude
                try? await firestoreService.updateUser(user)
            }
        }
        
        // Stop updating to save battery
        locationManager.stopUpdatingLocation()
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
        // Don't automatically fall back - let the user decide
    }
    
    // MARK: - Load Current User
    
    /// Loads the current user's profile
    @MainActor
    func loadCurrentUser() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        do {
            currentUser = try await firestoreService.fetchUser(userID: userID)
            
            // Use user's stored location if available
            if let user = currentUser, user.latitude != 0.0 && user.longitude != 0.0 {
                userLocation = CLLocationCoordinate2D(
                    latitude: user.latitude,
                    longitude: user.longitude
                )
            }
        } catch {
            errorMessage = "Failed to load profile: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Load Nearby Users
    
    /// Loads nearby users within the selected radius
    @MainActor
    func loadNearbyUsers() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        guard let location = userLocation else {
            errorMessage = "Location not available."
            return
        }
        
        guard let currentUser = currentUser else {
            errorMessage = "Current user profile not loaded."
            return
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            // Fetch nearby users from Firestore
            let users = try await firestoreService.fetchNearbyUsers(
                center: location,
                radiusMiles: selectedRadius,
                excludeUserID: userID
            )
            
            print("🔍 HomeViewModel: Found \(users.count) users within \(selectedRadius) miles")
            
            // Filter to only completed profiles
            let completedProfiles = users.filter { $0.isProfileComplete }
            print("✅ HomeViewModel: \(completedProfiles.count) have completed profiles")
            
            // Debug: Log current user's activities and time slots
            print("👤 Current User Activities: \(currentUser.activities.map { "\($0.name) (ID: \($0.id))" })")
            print("📅 Current User Times: \(currentUser.daySlotCombos.map { $0.displayName })")
            
            // Filter to only users who match (share at least 1 activity AND 1 time slot)
            let matchingService = MatchingService.shared
            let matchedUsers = completedProfiles.filter { user in
                let hasActivityOverlap = matchingService.hasOverlappingActivities(user1: currentUser, user2: user)
                let hasTimeOverlap = matchingService.hasOverlappingTimes(user1: currentUser, user2: user)
                let shouldMatch = matchingService.shouldMatch(user1: currentUser, user2: user)
                
                print("👥 Checking user: \(user.displayName)")
                print("   Activities: \(user.activities.map { "\($0.name) (ID: \($0.id))" })")
                print("   Times: \(user.daySlotCombos.map { $0.displayName })")
                print("   Activity Overlap: \(hasActivityOverlap)")
                print("   Time Overlap: \(hasTimeOverlap)")
                print("   Should Match: \(shouldMatch)")
                
                return shouldMatch
            }
            
            print("💚 HomeViewModel: \(matchedUsers.count) users match criteria")
            
            // Sort by distance (closest first)
            nearbyUsers = matchedUsers.sorted { user1, user2 in
                let location1 = CLLocation(latitude: user1.latitude, longitude: user1.longitude)
                let location2 = CLLocation(latitude: user2.latitude, longitude: user2.longitude)
                let currentLocation = CLLocation(latitude: location.latitude, longitude: location.longitude)
                
                let distance1 = currentLocation.distance(from: location1)
                let distance2 = currentLocation.distance(from: location2)
                
                return distance1 < distance2
            }
            
        } catch {
            errorMessage = "Failed to load nearby users: \(error.localizedDescription)"
            print("❌ HomeViewModel error: \(error)")
        }
    }
    
    // MARK: - Utility
    
    /// Clears error message
    func clearError() {
        errorMessage = nil
    }
}
