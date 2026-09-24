//
//  PhotoPickerView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import PhotosUI

/// Step 2: Upload exactly 3 photos from camera roll
struct PhotoPickerView: View {
    @Bindable var viewModel: ProfileViewModel
    let onNext: () -> Void
    let onBack: () -> Void
    
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var showImagePicker = false
    @State private var fullScreenPhoto: UIImage? = nil
    @State private var showFullScreenPhoto = false
    @State private var isLoadingPhotos = false
    
    var canContinue: Bool {
        viewModel.selectedPhotos.count == 3
    }
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Text("Add Your Photos")
                            .font(.system(size: 32, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)
                        
                        Text("Choose 3 photos that show the real you.\nNo filters needed!")
                            .font(.system(size: 17, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                    }
                    .padding(.top, 20)
                    .padding(.horizontal, 40)
                    
                    // Photo slots
                    VStack(spacing: 16) {
                        // Display existing photos with proper indexing
                        ForEach(Array(viewModel.selectedPhotos.enumerated()), id: \.offset) { index, photo in
                            PhotoSlot(
                                slotNumber: index + 1,
                                photo: photo,
                                onTap: {
                                    fullScreenPhoto = photo
                                    showFullScreenPhoto = true
                                },
                                onAdd: { showImagePicker = true },
                                onRemove: {
                                    print("🗑️ Removing photo at index \(index)")
                                    viewModel.removePhoto(at: index)
                                }
                            )
                        }
                        
                        // Display empty slots for remaining photos
                        ForEach(viewModel.selectedPhotos.count..<3, id: \.self) { index in
                            PhotoSlot(
                                slotNumber: index + 1,
                                photo: nil,
                                onTap: {},
                                onAdd: { showImagePicker = true },
                                onRemove: {}
                            )
                        }
                    }
                    .padding(.horizontal, 32)
                    
                    // Tips card
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: "lightbulb.fill")
                                .foregroundColor(Color.appPrimary)
                            
                            Text("Photo Tips")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appTextBody)
                        }
                        
                        VStack(alignment: .leading, spacing: 6) {
                            TipRow(text: "Choose clear, recent photos")
                            TipRow(text: "Smiling faces work best")
                            TipRow(text: "Show your personality!")
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.appPrimary.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal, 32)
                    .padding(.bottom, 100)
                }
            }
            .scrollIndicators(.hidden)
            
            // Bottom buttons
            VStack(spacing: 12) {
                // Continue button
                Button {
                    onNext()
                } label: {
                    Text("Continue")
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            canContinue ?
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            ) :
                            LinearGradient(
                                colors: [Color.gray.opacity(0.3), Color.gray.opacity(0.3)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                        .shadow(
                            color: canContinue ?
                            Color.appPrimary.opacity(0.3) :
                            Color.clear,
                            radius: 12,
                            x: 0,
                            y: 6
                        )
                }
                .disabled(!canContinue)
                
                // Back button
                Button {
                    onBack()
                } label: {
                    Text("Back")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    colors: [
                        Color.appCardBackground.opacity(0.95),
                        Color.appCardBackground.opacity(0.95)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
            )
        }
        .photosPicker(
            isPresented: $showImagePicker,
            selection: $selectedItems,
            maxSelectionCount: 3 - viewModel.selectedPhotos.count,
            matching: .images
        )
        .onChange(of: selectedItems) { oldValue, newValue in
            Task {
                await loadImages(from: newValue)
            }
        }
        .sheet(isPresented: $showFullScreenPhoto) {
            if let photo = fullScreenPhoto {
                FullScreenPhotoView(photo: photo, isPresented: $showFullScreenPhoto)
                    .presentationDragIndicator(.visible)
                    .interactiveDismissDisabled(false)
            }
        }
        .overlay {
            // Loading overlay when processing photos
            if isLoadingPhotos {
                ZStack {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                    
                    VStack(spacing: 16) {
                        ProgressView()
                            .tint(Color.appPrimary)
                            .scaleEffect(1.5)
                        
                        Text("Loading photos...")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)
                    }
                    .padding(24)
                    .background(Color.appCardBackground)
                    .cornerRadius(16)
                    .shadow(radius: 20)
                }
            }
        }
    }
    
    /// Loads and processes images sequentially to preserve selection order
    private func loadImages(from items: [PhotosPickerItem]) async {
        guard !items.isEmpty else { return }
        
        // Show loading indicator
        await MainActor.run {
            isLoadingPhotos = true
        }
        
        // Process images sequentially to maintain order
        for item in items {
            // Load image data
            guard let data = try? await item.loadTransferable(type: Data.self) else {
                continue
            }
            
            // Decode and resize image on background thread
            if let processedImage = await Self.processImage(from: data) {
                // Add photo to view model on main thread
                await MainActor.run {
                    viewModel.addPhoto(processedImage)
                }
            }
        }
        
        // Clear selection and hide loading indicator
        await MainActor.run {
            selectedItems = []
            isLoadingPhotos = false
        }
    }
    
    /// Process and resize image on background thread
    /// Resizes to max 1024px to reduce memory footprint
    static func processImage(from data: Data) async -> UIImage? {
        // This entire function runs on a background thread
        guard let originalImage = UIImage(data: data) else {
            return nil
        }
        
        // Calculate target size (max 1024px on longest side)
        let maxDimension: CGFloat = 1024
        let size = originalImage.size
        
        guard size.width > maxDimension || size.height > maxDimension else {
            // Image is already small enough
            return originalImage
        }
        
        // Calculate new size maintaining aspect ratio
        let ratio = min(maxDimension / size.width, maxDimension / size.height)
        let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        
        // Resize image
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let resizedImage = renderer.image { _ in
            originalImage.draw(in: CGRect(origin: .zero, size: newSize))
        }
        
        return resizedImage
    }
}

/// Individual photo slot
struct PhotoSlot: View {
    let slotNumber: Int
    let photo: UIImage?
    let onTap: () -> Void
    let onAdd: () -> Void
    let onRemove: () -> Void
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            if let photo = photo {
                // Photo display - tappable for full screen
                Button(action: onTap) {
                    Image(uiImage: photo)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(maxWidth: .infinity)
                        .frame(height: 180)
                        .background(Color.appCardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.appPrimary, lineWidth: 2)
                        )
                }
                .buttonStyle(.plain)
                
                // Remove button
                Button {
                    print("🗑️ Remove button tapped for slot \(slotNumber)")
                    onRemove()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(.white, Color.appDanger)
                        .shadow(color: .black.opacity(0.3), radius: 2, x: 0, y: 1)
                }
                .padding(8)
                .zIndex(1)
            } else {
                // Empty slot
                Button(action: onAdd) {
                    VStack(spacing: 12) {
                        Image(systemName: "photo.badge.plus")
                            .font(.system(size: 40))
                            .foregroundColor(Color.appPrimary)
                        
                        Text("Photo \(slotNumber)")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 180)
                    .background(Color.appCardBackground.opacity(0.5))
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .strokeBorder(
                                style: StrokeStyle(lineWidth: 2, dash: [8, 4])
                            )
                            .foregroundColor(Color.appPrimary.opacity(0.4))
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Tip row helper
struct TipRow: View {
    let text: String
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 12))
                .foregroundColor(Color.appPrimary)
            
            Text(text)
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundColor(Color.appSecondaryText)
        }
    }
}

/// Full-screen photo preview with swipe-to-dismiss
struct FullScreenPhotoView: View {
    let photo: UIImage
    @Binding var isPresented: Bool
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var opacity: Double = 0.0
    
    var body: some View {
        ZStack {
            // Black background
            Color.black
                .ignoresSafeArea()
            
            // Image with zoom and pan support
            Image(uiImage: photo)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .scaleEffect(scale)
                .opacity(opacity)
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            scale = lastScale * value
                        }
                        .onEnded { _ in
                            lastScale = scale
                            // Reset if zoomed out too far
                            if scale < 1.0 {
                                withAnimation(.spring()) {
                                    scale = 1.0
                                    lastScale = 1.0
                                }
                            }
                        }
                )
                .onTapGesture(count: 2) {
                    // Double tap to reset zoom
                    withAnimation(.spring()) {
                        scale = 1.0
                        lastScale = 1.0
                    }
                }
        }
        .onAppear {
            // Smooth fade-in animation
            withAnimation(.easeIn(duration: 0.2)) {
                opacity = 1.0
            }
        }
    }
}

#Preview {
    ProfileSetupFlowView()
}
