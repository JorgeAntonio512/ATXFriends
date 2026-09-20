//
//  PhotosSettingsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import PhotosUI

/// Settings view for managing user's 3 profile photos
struct PhotosSettingsView: View {
    @Bindable var viewModel: ProfileViewModel
    
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var showImagePicker = false
    @State private var replacingIndex: Int? = nil
    
    // Loaded images from Firebase Storage URLs
    @State private var loadedImages: [Int: UIImage] = [:]
    @State private var isLoadingImages = false
    
    // Per-slot loading states
    @State private var loadingSlots: Set<Int> = []
    
    var body: some View {
        ZStack {
            // Warm gradient background
            LinearGradient(
                colors: [
                    Color.white,
                    Color.white
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            ScrollView {
                VStack(spacing: 32) {
                    // Header
                    VStack(spacing: 12) {
                        Text("Your Photos")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                        
                        Text("Tap any photo to replace it")
                            .font(.system(size: 16, weight: .regular, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)
                    
                    // Photo slots in horizontal row
                    HStack(spacing: 16) {
                        ForEach(0..<3, id: \.self) { index in
                            ZStack {
                                if isLoadingImages {
                                    // Initial loading state for all slots
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color.white)
                                            .frame(width: 100, height: 100)
                                        
                                        ProgressView()
                                            .tint(Color.appPrimary)
                                    }
                                } else if let image = loadedImages[index] {
                                    // Photo loaded
                                    PhotoThumbnail(
                                        image: image,
                                        onTap: {
                                            replacingIndex = index
                                            showImagePicker = true
                                        },
                                        onDelete: {
                                            replacingIndex = index
                                            showImagePicker = true
                                        }
                                    )
                                } else {
                                    // Empty slot
                                    EmptyPhotoThumbnail(
                                        onTap: {
                                            replacingIndex = index
                                            showImagePicker = true
                                        }
                                    )
                                }
                                
                                // Per-slot loading overlay
                                if loadingSlots.contains(index) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 16)
                                            .fill(Color.black.opacity(0.6))
                                            .frame(width: 100, height: 100)
                                        
                                        VStack(spacing: 8) {
                                            ProgressView()
                                                .tint(.white)
                                            
                                            Text("Uploading...")
                                                .font(.system(size: 10, weight: .medium, design: .rounded))
                                                .foregroundColor(.white)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 32)
                    
                    // Tips
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: "lightbulb.fill")
                                .foregroundColor(Color.appPrimary)
                            
                            Text("Photo Tips")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        }
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text("• Choose clear, recent photos")
                            Text("• Smiling faces work best")
                            Text("• Show your personality!")
                        }
                        .font(.system(size: 14, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .lineSpacing(2)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.appPrimary.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                    .padding(.bottom, 40)
                }
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle("Photos")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            // Load photos from URLs when view appears
            await loadPhotosFromURLs()
        }
        .photosPicker(
            isPresented: $showImagePicker,
            selection: $selectedItems,
            maxSelectionCount: 1,
            matching: .images
        )
        .onChange(of: selectedItems) { oldValue, newValue in
            Task {
                await loadImage(from: newValue.first)
            }
        }
    }
    
    private func loadPhotosFromURLs() async {
        guard !viewModel.photoURLs.isEmpty else { return }
        
        await MainActor.run {
            isLoadingImages = true
        }
        
        // Load all photos concurrently
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, urlString) in viewModel.photoURLs.enumerated() {
                guard index < 3 else { break }
                
                group.addTask {
                    // Convert Firebase Storage URL to HTTPS URL if needed
                    var downloadURL = urlString
                    
                    // Handle gs:// URLs by converting to https://
                    if urlString.hasPrefix("gs://") {
                        // Extract bucket and path
                        let gsPath = urlString.replacingOccurrences(of: "gs://", with: "")
                        if let bucketEnd = gsPath.firstIndex(of: "/") {
                            let bucket = String(gsPath[..<bucketEnd])
                            let path = String(gsPath[bucketEnd...].dropFirst())
                            let encodedPath = path.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? path
                            downloadURL = "https://firebasestorage.googleapis.com/v0/b/\(bucket)/o/\(encodedPath)?alt=media"
                        }
                    }
                    
                    // Download image
                    if let url = URL(string: downloadURL),
                       let (data, _) = try? await URLSession.shared.data(from: url),
                       let image = UIImage(data: data) {
                        return (index, image)
                    }
                    return (index, nil)
                }
            }
            
            // Collect results
            for await (index, image) in group {
                if let image = image {
                    await MainActor.run {
                        loadedImages[index] = image
                    }
                }
            }
        }
        
        await MainActor.run {
            isLoadingImages = false
        }
    }
    
    private func loadImage(from item: PhotosPickerItem?) async {
        guard let item = item, let index = replacingIndex else { return }
        
        // Mark this slot as loading
        _ = await MainActor.run {
            loadingSlots.insert(index)
        }
        
        defer {
            // Clear loading state and reset
            Task { @MainActor in
                loadingSlots.remove(index)
                selectedItems = []
                replacingIndex = nil
            }
        }
        
        // Process image on background thread
        guard let data = try? await item.loadTransferable(type: Data.self),
              let processedImage = await processImage(from: data) else {
            await MainActor.run {
                viewModel.errorMessage = "Failed to process the selected image."
            }
            return
        }
        
        // Update the specific photo slot
        let success = await viewModel.updateSinglePhoto(processedImage, at: index)
        
        if success {
            // Immediately update the displayed image
            await MainActor.run {
                loadedImages[index] = processedImage
            }
            
            // Optionally reload just this photo's URL to confirm
            // (The photo URL should already be updated by updateSinglePhoto)
        } else {
            await MainActor.run {
                // Error message already set by viewModel
                print("❌ Failed to update photo at index \(index)")
            }
        }
    }
    
    private func processImage(from data: Data) async -> UIImage? {
        guard let originalImage = UIImage(data: data) else {
            return nil
        }
        
        let maxDimension: CGFloat = 1024
        let size = originalImage.size
        
        guard size.width > maxDimension || size.height > maxDimension else {
            return originalImage
        }
        
        let ratio = min(maxDimension / size.width, maxDimension / size.height)
        let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resizedImage = renderer.image { _ in
            originalImage.draw(in: CGRect(origin: .zero, size: newSize))
        }
        
        return resizedImage
    }
}

/// Photo thumbnail with delete button
struct PhotoThumbnail: View {
    let image: UIImage
    let onTap: () -> Void
    let onDelete: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .topTrailing) {
                // Photo
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 100, height: 100)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.appPrimary, lineWidth: 2)
                    )
                
                // Delete button (red X)
                Button(action: onDelete) {
                    ZStack {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 28, height: 28)
                        
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .shadow(color: .black.opacity(0.3), radius: 3, x: 0, y: 2)
                }
                .offset(x: 8, y: -8)
            }
        }
        .buttonStyle(.plain)
    }
}

/// Empty photo thumbnail placeholder
struct EmptyPhotoThumbnail: View {
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.appPrimary.opacity(0.2))
                    .frame(width: 100, height: 100)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(
                                Color.appPrimary,
                                style: StrokeStyle(lineWidth: 2, dash: [6, 4])
                            )
                    )
                
                Image(systemName: "plus")
                    .font(.system(size: 32, weight: .medium))
                    .foregroundColor(Color.appPrimary)
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        PhotosSettingsView(viewModel: ProfileViewModel())
    }
}
