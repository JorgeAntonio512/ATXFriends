//
//  GoogleSignInHelper.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/15/26.
//

import Foundation
import GoogleSignIn
import FirebaseAuth
import UIKit
import FirebaseCore

/// Helper class to manage Google Sign In authorization flow
class GoogleSignInHelper {
    
    /// Initiates the Google Sign In flow
    /// - Parameter completion: Callback with the Firebase AuthCredential or error
    func signIn(completion: @escaping (Result<AuthCredential, Error>) -> Void) {
        // Get the client ID from Firebase
        guard let clientID = Auth.auth().app?.options.clientID else {
            completion(.failure(GoogleSignInError.missingClientID))
            return
        }
        
        // Create Google Sign In configuration
        let config = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.configuration = config
        
        // Get the root view controller
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            completion(.failure(GoogleSignInError.noRootViewController))
            return
        }
        
        // Start the sign in flow
        GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { result, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                completion(.failure(GoogleSignInError.invalidCredentials))
                return
            }
            
            let accessToken = user.accessToken.tokenString
            
            // Create Firebase credential
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: accessToken
            )
            
            completion(.success(credential))
        }
    }
    
    /// Signs out the current Google user
    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }
}

// MARK: - Custom Errors

enum GoogleSignInError: LocalizedError {
    case missingClientID
    case noRootViewController
    case invalidCredentials
    
    var errorDescription: String? {
        switch self {
        case .missingClientID:
            return "Google Sign In configuration is missing."
        case .noRootViewController:
            return "Unable to present Google Sign In."
        case .invalidCredentials:
            return "Failed to obtain valid credentials from Google."
        }
    }
}
