//
//  ProfileViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import SwiftUI
import CoreLocation

/// ViewModel for managing user profile creation and editing
/// Handles the 3-by-3 system: 3 photos, 3 activities, 3 day/slot combos
@Observable
final class ProfileViewModel {
    // MARK: - Published State
    
    /// Current user being edited (Firebase representation)
    var user: FirebaseUser?
    
    /// Display name
    var displayName: String = ""
    
    /// User's bio
    var bio: String = ""
    
    /// Profile photos (max 3)
    var selectedPhotos: [UIImage] = []
    
    /// Photo URLs after upload
    var photoURLs: [String] = []
    
    /// Selected activities (max 3)
    var selectedActivities: [Activity] = []
    
    /// Selected day/slot combinations (max 3)
    var selectedDaySlotCombos: [DaySlotCombo] = []
    
    /// All available activities from Firestore
    var allActivities: [Activity] = []
    
    /// User's location
    var userLocation: CLLocationCoordinate2D?
    
    /// User's search radius in miles
    var radiusMiles: Double = 10.0

    /// "Share My Location" mode — see LocationSharingMode
    var locationSharingMode: LocationSharingMode = .off

    /// When a sharing mode last wrote a location
    var locationUpdatedAt: Date?

    /// Error message to display
    var errorMessage: String?
    
    /// Whether an operation is in progress
    var isLoading: Bool = false
    
    /// Upload progress for photos (0.0 to 1.0)
    var uploadProgress: Double = 0.0
    
    // MARK: - Services
    
    private let firestoreService = FirestoreService.shared
    private let storageService = FirebaseStorageService.shared
    private let authService = FirebaseAuthService.shared
    
    // MARK: - Initialization
    
    init() {}
    
    // MARK: - Load User Profile
    
    /// Loads the current user's profile from Firestore
    @MainActor
    func loadUserProfile() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            if let fetchedUser = try await firestoreService.fetchUser(userID: userID) {
                user = fetchedUser
                
                // Populate form fields
                displayName = fetchedUser.displayName
                bio = fetchedUser.bio
                photoURLs = fetchedUser.photoURLs
                selectedActivities = fetchedUser.activities
                selectedDaySlotCombos = fetchedUser.daySlotCombos
                radiusMiles = fetchedUser.radiusMiles
                locationSharingMode = LocationSharingMode(rawValue: fetchedUser.locationSharingMode) ?? .off
                locationUpdatedAt = fetchedUser.locationUpdatedAt

                if fetchedUser.latitude != 0.0 && fetchedUser.longitude != 0.0 {
                    userLocation = CLLocationCoordinate2D(
                        latitude: fetchedUser.latitude,
                        longitude: fetchedUser.longitude
                    )
                }
            }
        } catch {
            errorMessage = "Failed to load profile: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Load Activities
    
    /// Loads all available activities from Firestore
    /// Seeds the database with predefined activities if empty
    @MainActor
    func loadActivities() async {
        isLoading = true
        defer { isLoading = false }
        
        do {
            // First, try to seed activities if needed
            let seededCount = try await firestoreService.seedActivitiesIfNeeded()
            if seededCount > 0 {
                print("✅ Seeded \(seededCount) activities to Firestore")
            }
            
            // Then fetch all activities
            allActivities = try await firestoreService.fetchActivities()
            print("✅ Loaded \(allActivities.count) activities from Firestore")
        } catch {
            print("❌ Failed to load activities: \(error.localizedDescription)")
            errorMessage = "Failed to load activities: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Activity Management
    
    /// Selects an activity (max 10 total). The first 3 selections become Main;
    /// after that, new picks are Extra. Removing a Main auto-promotes an Extra
    /// to keep the Main count at exactly 3 whenever total >= 3 (see deselectActivity).
    func selectActivity(_ activity: Activity) {
        guard selectedActivities.count < 10 else {
            errorMessage = "You can only select up to 10 activities."
            return
        }

        guard !selectedActivities.contains(where: { $0.id == activity.id }) else {
            errorMessage = "This activity is already selected."
            return
        }

        let mainCount = selectedActivities.filter { $0.isPrimary }.count
        var newActivity = activity
        newActivity.isPrimary = mainCount < 3
        selectedActivities.append(newActivity)
        clearError()
    }

    /// Deselects an activity. If this removed one of the 3 Mains and other
    /// activities remain, auto-promotes the next Extra so there are always
    /// exactly 3 Mains whenever the total is >= 3.
    func deselectActivity(_ activity: Activity) {
        selectedActivities.removeAll { $0.id == activity.id }

        let mainCount = selectedActivities.filter { $0.isPrimary }.count
        if mainCount < 3, let promoteIndex = selectedActivities.firstIndex(where: { !$0.isPrimary }) {
            selectedActivities[promoteIndex].isPrimary = true
        }
    }

    /// Promotes an Extra activity to Main. Since there are always exactly 3 Mains,
    /// promoting one demotes the last-ordered current Main to Extra — tapping the
    /// star again on that one swaps it right back.
    func toggleMain(for activity: Activity) {
        guard let index = selectedActivities.firstIndex(where: { $0.id == activity.id }) else { return }
        guard !selectedActivities[index].isPrimary else { return }

        if let lastMainIndex = selectedActivities.lastIndex(where: { $0.isPrimary }) {
            selectedActivities[lastMainIndex].isPrimary = false
        }
        selectedActivities[index].isPrimary = true
    }

    /// Adds a new custom activity, or reuses an existing one if the (trimmed,
    /// whitespace-collapsed, case-insensitive) name already matches one.
    /// - Parameter category: Required category for a genuinely new activity;
    ///   ignored when the name matches an existing entry.
    @MainActor
    func addCustomActivity(name: String, category: ActivityCategory) async -> Bool {
        let trimmed = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)

        guard !trimmed.isEmpty else {
            errorMessage = "Activity name cannot be empty."
            return false
        }

        // Reuse an existing activity if it matches once normalized — no duplicate created.
        let normalizedKey = Activity.normalizedForComparison(trimmed)
        if let existing = allActivities.first(where: { Activity.normalizedForComparison($0.name) == normalizedKey }) {
            selectActivity(existing)
            return true
        }

        isLoading = true
        defer { isLoading = false }

        do {
            let activity = Activity(name: trimmed, isUserAdded: true)
            try await firestoreService.addActivity(activity, category: category)

            // Add to local list
            allActivities.append(activity)
            allActivities.sort { $0.name < $1.name }

            // Auto-select the new activity
            selectActivity(activity)

            return true
        } catch {
            errorMessage = "Failed to add activity: \(error.localizedDescription)"
            return false
        }
    }
    
    /// Searches activities by name
    @MainActor
    func searchActivities(searchText: String) async -> [Activity] {
        guard !searchText.isEmpty else {
            return allActivities
        }
        
        do {
            return try await firestoreService.searchActivities(searchText: searchText)
        } catch {
            errorMessage = "Search failed: \(error.localizedDescription)"
            return []
        }
    }
    
    // MARK: - Day/Slot Combo Management
    
    /// Selects a day/slot combination. No maximum — picking more just means
    /// "I'm free a lot," which is good for matching.
    func selectDaySlotCombo(day: DayOfWeek, slot: TimeSlot) {
        let combo = DaySlotCombo(dayOfWeek: day, timeSlot: slot)
        
        guard !selectedDaySlotCombos.contains(where: { $0 == combo }) else {
            errorMessage = "This time slot is already selected."
            return
        }
        
        selectedDaySlotCombos.append(combo)
        clearError()
    }
    
    /// Deselects a day/slot combination
    func deselectDaySlotCombo(_ combo: DaySlotCombo) {
        selectedDaySlotCombos.removeAll { $0 == combo }
    }
    
    // MARK: - Photo Management
    
    /// Adds a photo (max 3)
    /// Should be called on main thread with pre-processed images
    @MainActor
    func addPhoto(_ photo: UIImage) {
        guard selectedPhotos.count < 3 else {
            errorMessage = "You can only upload 3 photos."
            return
        }
        
        selectedPhotos.append(photo)
        clearError()
    }
    
    /// Removes a photo at the specified index
    @MainActor
    func removePhoto(at index: Int) {
        guard index >= 0 && index < selectedPhotos.count else { return }
        selectedPhotos.remove(at: index)
    }
    
    /// Replaces a photo at the specified index
    @MainActor
    func replacePhoto(at index: Int, with photo: UIImage) {
        guard index >= 0 && index < selectedPhotos.count else { return }
        selectedPhotos[index] = photo
    }
    
    /// Updates a single photo at a specific slot
    /// Uploads ONLY that photo to Firebase Storage and updates ONLY that slot in Firestore
    /// - Parameters:
    ///   - photo: The new UIImage to upload
    ///   - index: The slot index (0, 1, or 2)
    /// - Returns: True if successful, false otherwise
    @MainActor
    func updateSinglePhoto(_ photo: UIImage, at index: Int) async -> Bool {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return false
        }
        
        guard index >= 0 && index < 3 else {
            errorMessage = "Invalid photo slot."
            return false
        }
        
        do {
            // Upload only this specific photo
            let newPhotoURL = try await storageService.uploadProfilePhoto(
                image: photo,
                userID: userID,
                photoIndex: index
            )
            
            // Update the photoURLs array
            var updatedPhotoURLs = photoURLs
            
            // Ensure the array is large enough
            while updatedPhotoURLs.count <= index {
                updatedPhotoURLs.append("")
            }
            
            // Update only the specific slot
            updatedPhotoURLs[index] = newPhotoURL
            
            // Update Firestore with the new photoURLs array
            if var existingUser = user {
                existingUser.photoURLs = updatedPhotoURLs
                existingUser.updatedAt = Date()
                
                try await firestoreService.updateUser(existingUser)
                
                // Update local state
                user = existingUser
                photoURLs = updatedPhotoURLs
                
                print("✅ Successfully updated photo at slot \(index)")
                return true
            } else {
                errorMessage = "User profile not found."
                return false
            }
        } catch {
            errorMessage = "Failed to update photo: \(error.localizedDescription)"
            print("❌ Error updating photo at slot \(index): \(error)")
            return false
        }
    }
    
    // MARK: - Location Management

    /// Updates the user's location
    func updateLocation(_ coordinate: CLLocationCoordinate2D) {
        userLocation = coordinate
    }

    /// Writes a "Share My Location" mode change (and, when provided, a freshly
    /// fetched coarse coordinate) directly — bypasses full-profile validation since
    /// this only ever touches the sharing fields.
    @MainActor
    func updateLocationSharing(mode: LocationSharingMode, coordinate: CLLocationCoordinate2D? = nil) async -> Bool {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return false
        }
        do {
            try await firestoreService.updateLocationSharing(userID: userID, mode: mode, coordinate: coordinate)
            locationSharingMode = mode
            if let coordinate {
                locationUpdatedAt = Date()
                userLocation = coordinate
            }
            user?.locationSharingMode = mode.rawValue
            if let coordinate {
                user?.locationUpdatedAt = locationUpdatedAt
                user?.latitude = coordinate.latitude
                user?.longitude = coordinate.longitude
            }
            return true
        } catch {
            errorMessage = "Failed to update location sharing: \(error.localizedDescription)"
            return false
        }
    }
    
    /// Updates the search radius
    func updateRadius(_ miles: Double) {
        radiusMiles = max(1.0, min(miles, 50.0)) // Clamp between 1-50 miles
    }
    
    // MARK: - Profile Validation
    
    /// Validates that the profile meets the 3-by-3 requirements
    func validateProfile() -> Bool {
        // Check display name
        guard !displayName.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Please enter your name."
            return false
        }
        
        // Check photos (either 3 selected or 3 URLs if editing)
        guard selectedPhotos.count == 3 || photoURLs.count == 3 else {
            errorMessage = "Please upload exactly 3 photos."
            return false
        }
        
        // Check activities: 3-10 total, exactly 3 marked Main
        guard (3...10).contains(selectedActivities.count) else {
            errorMessage = "Please select 3 to 10 activities."
            return false
        }

        guard selectedActivities.filter({ $0.isPrimary }).count == 3 else {
            errorMessage = "Please choose exactly 3 Main activities."
            return false
        }

        // Check day/slot combos: at least 3, no maximum
        guard selectedDaySlotCombos.count >= 3 else {
            errorMessage = "Please select at least 3 time slots."
            return false
        }
        
        // Check location
        guard let _ = userLocation else {
            errorMessage = "Please enable location access."
            return false
        }
        
        return true
    }
    
    // MARK: - Save Profile
    
    /// Saves the user profile to Firestore (critical data only)
    /// This method saves essential profile data immediately for quick navigation
    /// Photo uploads can happen in background
    @MainActor
    func saveProfileCriticalData() async -> Bool {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return false
        }
        
        // Validate profile
        guard validateProfile() else {
            return false
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            // For now, use empty photo URLs or existing ones
            // Photos will be uploaded in background
            let finalPhotoURLs = photoURLs.isEmpty ? [] : photoURLs
            
            // Create or update user with critical data
            let updatedUser = FirebaseUser(
                id: userID,
                displayName: displayName.trimmingCharacters(in: .whitespaces),
                photoURLs: finalPhotoURLs,
                activities: selectedActivities,
                daySlotCombos: selectedDaySlotCombos,
                latitude: userLocation?.latitude ?? 0.0,
                longitude: userLocation?.longitude ?? 0.0,
                radiusMiles: radiusMiles,
                createdAt: user?.createdAt ?? Date(),
                updatedAt: Date(),
                isProfileComplete: true,
                bio: bio,
                locationSharingMode: user?.locationSharingMode ?? LocationSharingMode.off.rawValue,
                locationUpdatedAt: user?.locationUpdatedAt
            )
            
            // Save to Firestore immediately
            if user == nil {
                // Creating new profile
                try await firestoreService.createUser(updatedUser)
            } else {
                // Updating existing profile
                try await firestoreService.updateUser(updatedUser)
            }
            
            // Update local user
            user = updatedUser
            
            return true
        } catch {
            errorMessage = "Failed to save profile: \(error.localizedDescription)"
            return false
        }
    }
    
    /// Uploads photos in background after profile is saved
    /// Call this after navigating to avoid blocking UI
    @MainActor
    func uploadPhotosInBackground() async {
        guard let userID = authService.currentUserID else { return }
        guard selectedPhotos.count == 3 else { return }
        
        uploadProgress = 0.1
        
        do {
            // Upload photos
            let uploadedURLs = try await storageService.uploadProfilePhotos(
                images: selectedPhotos,
                userID: userID
            )
            
            uploadProgress = 0.7
            
            // Update user document with photo URLs
            if var existingUser = user {
                existingUser.photoURLs = uploadedURLs
                try await firestoreService.updateUser(existingUser)
                
                // Update local state
                user = existingUser
                photoURLs = uploadedURLs
            }
            
            uploadProgress = 1.0
            print("✅ Photos uploaded successfully in background")
        } catch {
            print("❌ Background photo upload failed: \(error.localizedDescription)")
            errorMessage = "Photo upload failed. Please try updating your profile from settings."
        }
    }
    
    /// Saves the user profile to Firestore
    /// Uploads photos if needed and creates/updates user document
    @MainActor
    func saveProfile() async -> Bool {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return false
        }
        
        // Validate profile
        guard validateProfile() else {
            return false
        }
        
        isLoading = true
        uploadProgress = 0.0
        defer { 
            isLoading = false
            uploadProgress = 0.0
        }
        
        do {
            // Upload photos if new photos were selected
            var finalPhotoURLs = photoURLs
            
            if selectedPhotos.count == 3 {
                uploadProgress = 0.1
                
                // Upload photos
                finalPhotoURLs = try await storageService.uploadProfilePhotos(
                    images: selectedPhotos,
                    userID: userID
                )
                
                uploadProgress = 0.5
            }
            
            // Create or update user
            let updatedUser = FirebaseUser(
                id: userID,
                displayName: displayName.trimmingCharacters(in: .whitespaces),
                photoURLs: finalPhotoURLs,
                activities: selectedActivities,
                daySlotCombos: selectedDaySlotCombos,
                latitude: userLocation?.latitude ?? 0.0,
                longitude: userLocation?.longitude ?? 0.0,
                radiusMiles: radiusMiles,
                createdAt: user?.createdAt ?? Date(),
                updatedAt: Date(),
                isProfileComplete: true,
                bio: bio,
                locationSharingMode: user?.locationSharingMode ?? LocationSharingMode.off.rawValue,
                locationUpdatedAt: user?.locationUpdatedAt
            )
            
            uploadProgress = 0.7
            
            // Save to Firestore
            if user == nil {
                // Creating new profile
                try await firestoreService.createUser(updatedUser)
            } else {
                // Updating existing profile
                try await firestoreService.updateUser(updatedUser)
            }
            
            uploadProgress = 1.0
            
            // Update local user
            user = updatedUser
            photoURLs = finalPhotoURLs
            
            return true
        } catch {
            errorMessage = "Failed to save profile: \(error.localizedDescription)"
            return false
        }
    }
    
    // MARK: - Utility Methods
    
    /// Clears error message
    func clearError() {
        errorMessage = nil
    }
    
    /// Resets the profile form
    func resetForm() {
        displayName = ""
        bio = ""
        selectedPhotos = []
        photoURLs = []
        selectedActivities = []
        selectedDaySlotCombos = []
        userLocation = nil
        radiusMiles = 10.0
        errorMessage = nil
    }
    
    /// Checks if the profile is complete
    var isProfileComplete: Bool {
        return !displayName.isEmpty &&
               (selectedPhotos.count == 3 || photoURLs.count == 3) &&
               (3...10).contains(selectedActivities.count) &&
               selectedActivities.filter({ $0.isPrimary }).count == 3 &&
               selectedDaySlotCombos.count >= 3 &&
               userLocation != nil
    }

    /// Returns progress percentage for profile completion
    var profileCompletionPercentage: Double {
        var completed = 0.0
        let total = 5.0

        if !displayName.isEmpty { completed += 1 }
        if selectedPhotos.count == 3 || photoURLs.count == 3 { completed += 1 }
        if (3...10).contains(selectedActivities.count) && selectedActivities.filter({ $0.isPrimary }).count == 3 { completed += 1 }
        if selectedDaySlotCombos.count >= 3 { completed += 1 }
        if userLocation != nil { completed += 1 }

        return (completed / total) * 100
    }
}
