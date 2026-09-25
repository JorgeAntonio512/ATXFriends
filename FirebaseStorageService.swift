//
//  FirebaseStorageService.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import FirebaseStorage
import UIKit

/// Service layer for Firebase Storage operations
/// Handles uploading and retrieving profile photos
final class FirebaseStorageService {
    // MARK: - Properties
    
    private let storage = Storage.storage()
    private let profilePhotosPath = "profile_photos"
    
    // Image upload settings - adjust these to balance quality vs upload speed
    private let maxImageDimension: CGFloat = 1920 // Max width/height in pixels
    private let jpegQuality: CGFloat = 0.85 // 0.0 to 1.0 (0.85 is high quality, small file)
    
    // MARK: - Singleton
    
    static let shared = FirebaseStorageService()
    
    private init() {}
    
    // MARK: - Upload Operations
    
    /// Uploads a profile photo for a user
    /// - Parameters:
    ///   - image: The UIImage to upload
    ///   - userID: The user's Firebase UID
    ///   - photoIndex: The index of the photo (0, 1, or 2)
    /// - Returns: The download URL of the uploaded photo
    /// - Throws: Storage errors
    func uploadProfilePhoto(image: UIImage, userID: String, photoIndex: Int) async throws -> String {
        // Validate photo index
        guard photoIndex >= 0 && photoIndex < 3 else {
            throw StorageError.invalidPhotoIndex
        }
        
        // Resize and compress image for optimal upload
        let resizedImage = resizeImage(image, maxDimension: maxImageDimension)
        
        // Compress to JPEG with configured quality
        guard let imageData = resizedImage.jpegData(compressionQuality: jpegQuality) else {
            throw StorageError.imageCompressionFailed
        }
        
        let fileSizeKB = Double(imageData.count) / 1024.0
        let fileSizeMB = fileSizeKB / 1024.0
        print("📸 Uploading photo \(photoIndex): \(String(format: "%.1f", fileSizeKB))KB (\(String(format: "%.2f", fileSizeMB))MB)")
        
        // Create storage reference
        let fileName = "\(userID)_photo_\(photoIndex).jpg"
        let storageRef = storage.reference().child(profilePhotosPath).child(userID).child(fileName)
        
        // Set metadata
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        
        // Upload the data
        _ = try await storageRef.putDataAsync(imageData, metadata: metadata)
        
        // Get download URL
        let downloadURL = try await storageRef.downloadURL()
        
        return downloadURL.absoluteString
    }
    
    // MARK: - Image Processing Helpers
    
    /// Resizes an image to fit within a maximum dimension while maintaining aspect ratio
    /// - Parameters:
    ///   - image: The image to resize
    ///   - maxDimension: Maximum width or height in pixels
    /// - Returns: Resized image
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
    
    /// Uploads multiple profile photos for a user (in parallel for speed)
    /// - Parameters:
    ///   - images: Array of exactly 3 UIImages
    ///   - userID: The user's Firebase UID
    /// - Returns: Array of 3 download URLs
    /// - Throws: Storage errors
    func uploadProfilePhotos(images: [UIImage], userID: String) async throws -> [String] {
        guard images.count == 3 else {
            throw StorageError.invalidPhotoCount
        }
        
        // Upload all 3 photos in parallel using TaskGroup
        return try await withThrowingTaskGroup(of: (Int, String).self) { group in
            // Start all 3 uploads concurrently
            for (index, image) in images.enumerated() {
                group.addTask {
                    let url = try await self.uploadProfilePhoto(image: image, userID: userID, photoIndex: index)
                    return (index, url)
                }
            }
            
            // Collect results in the correct order
            var urls: [String?] = [nil, nil, nil]
            for try await (index, url) in group {
                urls[index] = url
            }
            
            // Ensure all uploads succeeded
            guard let url0 = urls[0], let url1 = urls[1], let url2 = urls[2] else {
                throw StorageError.uploadFailed
            }
            
            return [url0, url1, url2]
        }
    }
    
    /// Replaces a single profile photo
    /// - Parameters:
    ///   - image: The new UIImage
    ///   - userID: The user's Firebase UID
    ///   - photoIndex: The index of the photo to replace (0, 1, or 2)
    /// - Returns: The download URL of the new photo
    /// - Throws: Storage errors
    func replaceProfilePhoto(image: UIImage, userID: String, photoIndex: Int) async throws -> String {
        // Delete the old photo first (optional, but keeps storage clean)
        try? await deleteProfilePhoto(userID: userID, photoIndex: photoIndex)
        
        // Upload the new photo
        return try await uploadProfilePhoto(image: image, userID: userID, photoIndex: photoIndex)
    }
    
    // MARK: - Delete Operations
    
    /// Deletes a single profile photo
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - photoIndex: The index of the photo to delete (0, 1, or 2)
    /// - Throws: Storage errors
    func deleteProfilePhoto(userID: String, photoIndex: Int) async throws {
        guard photoIndex >= 0 && photoIndex < 3 else {
            throw StorageError.invalidPhotoIndex
        }
        
        let fileName = "\(userID)_photo_\(photoIndex).jpg"
        let storageRef = storage.reference().child(profilePhotosPath).child(userID).child(fileName)
        
        try await storageRef.delete()
    }
    
    // MARK: - Download Operations
    
    /// Downloads a profile photo from a URL
    /// - Parameter urlString: The download URL string
    /// - Returns: The UIImage if successful
    /// - Throws: Storage or network errors
    func downloadProfilePhoto(from urlString: String) async throws -> UIImage {
        guard let url = URL(string: urlString) else {
            throw StorageError.invalidURL
        }
        
        let (data, _) = try await URLSession.shared.data(from: url)
        
        guard let image = UIImage(data: data) else {
            throw StorageError.imageDecodingFailed
        }
        
        return image
    }
    
    /// Downloads multiple profile photos from URLs
    /// - Parameter urlStrings: Array of download URL strings
    /// - Returns: Array of UIImages
    /// - Throws: Storage or network errors
    func downloadProfilePhotos(from urlStrings: [String]) async throws -> [UIImage] {
        var images: [UIImage] = []
        
        for urlString in urlStrings {
            let image = try await downloadProfilePhoto(from: urlString)
            images.append(image)
        }
        
        return images
    }
    
    // MARK: - Utility Methods
    
    /// Gets the storage reference for a user's profile photo
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - photoIndex: The index of the photo (0, 1, or 2)
    /// - Returns: StorageReference
    func getPhotoReference(userID: String, photoIndex: Int) -> StorageReference {
        let fileName = "\(userID)_photo_\(photoIndex).jpg"
        return storage.reference().child(profilePhotosPath).child(userID).child(fileName)
    }
    
    /// Checks if a photo exists in storage
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - photoIndex: The index of the photo (0, 1, or 2)
    /// - Returns: True if the photo exists, false otherwise
    func photoExists(userID: String, photoIndex: Int) async -> Bool {
        let ref = getPhotoReference(userID: userID, photoIndex: photoIndex)
        
        do {
            _ = try await ref.getMetadata()
            return true
        } catch {
            return false
        }
    }
    
    /// Gets the total size of all photos for a user in bytes
    /// - Parameter userID: The user's Firebase UID
    /// - Returns: Total size in bytes
    func getTotalPhotoSize(userID: String) async throws -> Int64 {
        let userRef = storage.reference().child(profilePhotosPath).child(userID)
        let result = try await userRef.listAll()
        
        var totalSize: Int64 = 0
        
        for item in result.items {
            let metadata = try await item.getMetadata()
            totalSize += metadata.size
        }
        
        return totalSize
    }
}

// MARK: - Custom Errors

enum StorageError: LocalizedError {
    case invalidPhotoIndex
    case invalidPhotoCount
    case imageCompressionFailed
    case imageDecodingFailed
    case invalidURL
    case uploadFailed
    case downloadFailed
    
    var errorDescription: String? {
        switch self {
        case .invalidPhotoIndex:
            return "Photo index must be 0, 1, or 2."
        case .invalidPhotoCount:
            return "Exactly 3 profile photos are required."
        case .imageCompressionFailed:
            return "Failed to compress image data."
        case .imageDecodingFailed:
            return "Failed to decode image data."
        case .invalidURL:
            return "Invalid photo URL."
        case .uploadFailed:
            return "Failed to upload photo to storage."
        case .downloadFailed:
            return "Failed to download photo from storage."
        }
    }
}
