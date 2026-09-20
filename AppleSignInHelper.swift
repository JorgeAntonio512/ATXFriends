//
//  AppleSignInHelper.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/15/26.
//

import Foundation
import AuthenticationServices
import UIKit

/// Helper class to manage Apple Sign In authorization flow
class AppleSignInHelper: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var completion: ((Result<ASAuthorization, Error>) -> Void)?
    
    /// Initiates the Apple Sign In flow
    /// - Parameters:
    ///   - nonce: A unique nonce string for security
    ///   - completion: Callback with the authorization result
    func signIn(nonce: String, completion: @escaping (Result<ASAuthorization, Error>) -> Void) {
        self.completion = completion
        
        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = nonce
        
        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = self
        authorizationController.performRequests()
    }
    
    // MARK: - ASAuthorizationControllerDelegate
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        completion?(.success(authorization))
        completion = nil
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        completion?(.failure(error))
        completion = nil
    }
    
    // MARK: - ASAuthorizationControllerPresentationContextProviding
    
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else {
            // This should not happen in normal app lifecycle, but provide a safe fallback
            fatalError("No active window scene found for Apple Sign In presentation")
        }
        
        // Return existing window if available, otherwise create a new one
        return windowScene.windows.first ?? UIWindow(windowScene: windowScene)
    }
}
