//
//  CreateGroupView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import SwiftUI
import PhotosUI
import FirebaseStorage

/// View for creating a new group
struct CreateGroupView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var viewModel: GroupsViewModel
    
    @State private var activityName = ""
    @State private var location = ""
    @State private var dateTime = Date().addingTimeInterval(86400) // Default to tomorrow
    @State private var recurrence: Group.GroupRecurrence = .none
    @State private var privacy: Group.GroupPrivacy = .public
    @State private var description = ""
    @State private var minParticipants = 3
    @State private var maxParticipants = 8
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var coverPhotoURL: String?
    @State private var coverPhotoData: Data?
    @State private var isCreating = false
    @State private var showError = false
    @State private var errorMessage = ""
    
    private var isFormValid: Bool {
        !activityName.isEmpty &&
        !location.isEmpty &&
        !description.isEmpty &&
        minParticipants >= 3 &&
        maxParticipants >= minParticipants &&
        maxParticipants <= 30
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Background
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                Form {
                    // Basic Info Section
                    Section {
                        TextField("Activity Name", text: $activityName)
                            .font(.system(size: 16, design: .rounded))
                        
                        TextField("Location", text: $location)
                            .font(.system(size: 16, design: .rounded))
                        
                        DatePicker("Date & Time", selection: $dateTime, in: Date()...)
                            .font(.system(size: 16, design: .rounded))
                        
                        Picker("Recurrence", selection: $recurrence) {
                            ForEach(Group.GroupRecurrence.allCases, id: \.self) { recurrence in
                                Text(recurrence.label)
                                    .tag(recurrence)
                            }
                        }
                        .font(.system(size: 16, design: .rounded))
                        
                        Picker("Privacy", selection: $privacy) {
                            Text("Public").tag(Group.GroupPrivacy.public)
                            Text("Invite Only").tag(Group.GroupPrivacy.inviteOnly)
                        }
                        .font(.system(size: 16, design: .rounded))
                    } header: {
                        Text("Basic Info")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    }
                    
                    // Details Section
                    Section {
                        TextEditor(text: $description)
                            .font(.system(size: 16, design: .rounded))
                            .frame(minHeight: 80)
                            .overlay(alignment: .topLeading) {
                                if description.isEmpty {
                                    Text("Describe your group activity...")
                                        .font(.system(size: 16, design: .rounded))
                                        .foregroundColor(.secondary)
                                        .padding(.top, 8)
                                        .padding(.leading, 4)
                                        .allowsHitTesting(false)
                                }
                            }
                        
                        PhotosPicker(selection: $selectedPhoto, matching: .images) {
                            HStack {
                                Image(systemName: "photo")
                                    .foregroundColor(Color.appPrimary)
                                
                                if coverPhotoData != nil {
                                    Text("Cover Photo Selected")
                                        .font(.system(size: 16, design: .rounded))
                                        .foregroundColor(Color.appPrimary)
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundColor(Color.appPrimary)
                                } else {
                                    Text("Add Cover Photo (Optional)")
                                        .font(.system(size: 16, design: .rounded))
                                        .foregroundColor(Color.appPrimary)
                                }
                            }
                        }
                        .onChange(of: selectedPhoto) { _, newValue in
                            Task {
                                await loadPhotoData(from: newValue)
                            }
                        }
                    } header: {
                        Text("Details")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    }
                    
                    // Headcount Section
                    Section {
                        Stepper(value: $minParticipants, in: 3...20) {
                            HStack {
                                Text("Minimum")
                                    .font(.system(size: 16, design: .rounded))
                                Spacer()
                                Text("\(minParticipants)")
                                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appPrimary)
                            }
                        }
                        .onChange(of: minParticipants) { _, newMin in
                            // Ensure max is always >= min
                            if maxParticipants < newMin {
                                maxParticipants = newMin
                            }
                        }
                        
                        Stepper(value: $maxParticipants, in: minParticipants...30) {
                            HStack {
                                Text("Maximum")
                                    .font(.system(size: 16, design: .rounded))
                                Spacer()
                                Text("\(maxParticipants)")
                                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appPrimary)
                            }
                        }
                    } header: {
                        Text("Headcount")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    } footer: {
                        Text("Groups need at least 3 people. Set a maximum based on the activity.")
                            .font(.system(size: 13, design: .rounded))
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Create Group")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .font(.system(size: 17, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
                
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Create") {
                        Task {
                            await createGroup()
                        }
                    }
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(isFormValid ? Color.appPrimary : .secondary)
                    .disabled(!isFormValid || isCreating)
                }
            }
            .alert("Error", isPresented: $showError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage)
            }
            .overlay {
                if isCreating {
                    ZStack {
                        Color.black.opacity(0.3)
                            .ignoresSafeArea()
                        
                        VStack(spacing: 16) {
                            ProgressView()
                                .tint(Color.appPrimary)
                                .scaleEffect(1.3)
                            
                            Text("Creating group...")
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(.white)
                        }
                        .padding(32)
                        .background(Color(red: 0.35, green: 0.35, blue: 0.35))
                        .cornerRadius(16)
                    }
                }
            }
        }
    }
    
    private func createGroup() async {
        guard let userID = FirebaseAuthService.shared.currentUserID else {
            errorMessage = "You must be signed in to create a group."
            showError = true
            return
        }
        
        isCreating = true
        
        do {
            // Get user's location for geohash
            let user = try await FirestoreService.shared.fetchUser(userID: userID)
            guard let user = user else {
                throw NSError(domain: "CreateGroup", code: -1, userInfo: [NSLocalizedDescriptionKey: "Could not load user profile"])
            }
            
            // Generate geohash from user's location
            let geoHash = GeoHashUtility.encode(latitude: user.latitude, longitude: user.longitude, precision: 5)
            
            // Generate group ID
            let groupID = UUID().uuidString
            
            // Upload cover photo if selected
            var uploadedPhotoURL: String? = nil
            if let photoData = coverPhotoData {
                uploadedPhotoURL = try await uploadCoverPhoto(photoData: photoData, groupID: groupID)
            }
            
            let group = Group(
                id: groupID,
                activityName: activityName,
                location: location,
                dateTime: dateTime,
                recurrence: recurrence,
                description: description,
                coverPhotoURL: uploadedPhotoURL,
                minParticipants: minParticipants,
                maxParticipants: maxParticipants,
                privacy: privacy,
                status: .open,
                organizerID: userID,
                createdAt: Date(),
                confirmedAt: nil,
                confirmationDeadline: nil,
                geoHash: geoHash
            )
            
            try await viewModel.createGroup(group)
            
            // Reload groups to show the new one
            let userGeoHashPrefix = String(geoHash.prefix(4))
            await viewModel.loadGroups(userID: userID, geoHashPrefix: userGeoHashPrefix)
            
            isCreating = false
            dismiss()
        } catch {
            isCreating = false
            errorMessage = "Failed to create group: \(error.localizedDescription)"
            showError = true
        }
    }
    
    private func loadPhotoData(from item: PhotosPickerItem?) async {
        guard let item = item else {
            coverPhotoData = nil
            return
        }
        
        // Load the image data from the selected photo
        guard let data = try? await item.loadTransferable(type: Data.self) else {
            await MainActor.run {
                errorMessage = "Failed to load the selected image."
                showError = true
            }
            return
        }
        
        await MainActor.run {
            coverPhotoData = data
        }
    }
    
    private func uploadCoverPhoto(photoData: Data, groupID: String) async throws -> String {
        // Process and resize image if needed
        guard let uiImage = UIImage(data: photoData) else {
            throw NSError(domain: "CreateGroup", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to process image data"])
        }
        
        // Resize image to reasonable dimensions (max 1920px)
        let resizedImage = resizeImage(uiImage, maxDimension: 1920)
        
        // Compress to JPEG
        guard let imageData = resizedImage.jpegData(compressionQuality: 0.85) else {
            throw NSError(domain: "CreateGroup", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to compress image"])
        }
        
        // Create storage reference
        let storage = Storage.storage()
        let fileName = "\(UUID().uuidString).jpg"
        let storageRef = storage.reference().child("group_photos").child(groupID).child(fileName)
        
        // Set metadata
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        
        // Upload the data
        _ = try await storageRef.putDataAsync(imageData, metadata: metadata)
        
        // Get download URL
        let downloadURL = try await storageRef.downloadURL()
        
        return downloadURL.absoluteString
    }
    
    private func resizeImage(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let originalSize = image.size
        
        // If image is already smaller than max dimension, return as-is
        if originalSize.width <= maxDimension && originalSize.height <= maxDimension {
            return image
        }
        
        // Calculate new size maintaining aspect ratio
        let aspectRatio = originalSize.width / originalSize.height
        var newSize: CGSize
        
        if originalSize.width > originalSize.height {
            // Landscape or square
            newSize = CGSize(width: maxDimension, height: maxDimension / aspectRatio)
        } else {
            // Portrait
            newSize = CGSize(width: maxDimension * aspectRatio, height: maxDimension)
        }
        
        // Create new image with target size
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resizedImage = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
        
        return resizedImage
    }
}

#Preview {
    CreateGroupView(viewModel: GroupsViewModel())
}
