//
//  PlansViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/17/26.
//

import Foundation
import FirebaseAuth
import FirebaseFirestore

/// ViewModel for managing plans
@Observable
final class PlansViewModel {
    // MARK: - Published State
    
    var allPlans: [Plan] = []
    var isLoading: Bool = false
    var errorMessage: String?
    
    // MARK: - Services
    
    private let plansService = PlansService.shared
    private let firestoreService = FirestoreService.shared
    private let authService = FirebaseAuthService.shared
    
    // MARK: - Listener
    
    private var planListener: ListenerRegistration?
    
    // MARK: - Computed Properties
    
    /// Plans where current user is the receiver and status is pending/counter
    var incomingPlans: [Plan] {
        guard let userID = authService.currentUserID else { return [] }
        return allPlans.filter {
            $0.receiverID == userID &&
            ($0.status == .pending || $0.status == .counterProposed)
        }
        .sorted { $0.createdAt > $1.createdAt }
    }
    
    /// Plans where current user is the proposer and status is pending/counter
    var outgoingPlans: [Plan] {
        guard let userID = authService.currentUserID else { return [] }
        return allPlans.filter {
            $0.proposerID == userID &&
            ($0.status == .pending || $0.status == .counterProposed)
        }
        .sorted { $0.createdAt > $1.createdAt }
    }
    
    /// Confirmed plans (upcoming hangouts)
    var upcomingPlans: [Plan] {
        allPlans.filter { $0.status == .confirmed }
            .sorted { ($0.confirmedDate ?? Date.distantFuture) < ($1.confirmedDate ?? Date.distantFuture) }
    }
    
    /// Past plans (for history)
    var pastPlans: [Plan] {
        allPlans.filter {
            if let confirmedDate = $0.confirmedDate {
                return $0.status == .confirmed && confirmedDate < Date()
            }
            return false
        }
        .sorted { ($0.confirmedDate ?? Date.distantPast) > ($1.confirmedDate ?? Date.distantPast) }
    }
    
    // MARK: - Load Plans
    
    @MainActor
    func loadPlans() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user signed in"
            return
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            allPlans = try await plansService.fetchPlans(for: userID)
        } catch {
            errorMessage = "Failed to load plans: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Setup Listener
    
    func setupListener() {
        guard let userID = authService.currentUserID else { return }
        
        // Remove existing listener if any
        planListener?.remove()
        
        planListener = plansService.listenToPlans(for: userID) { [weak self] plans in
            Task { @MainActor in
                self?.allPlans = plans
            }
        }
    }
    
    func removeListener() {
        planListener?.remove()
        planListener = nil
    }
    
    // MARK: - Create Plan
    
    @MainActor
    func createPlan(
        matchID: String,
        receiverID: String,
        activity: Activity,
        location: String?,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil,
        proposedDates: [Date]
    ) async -> Bool {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user signed in"
            return false
        }
        
        guard proposedDates.count >= 1 else {
            errorMessage = "Must select at least 1 date"
            return false
        }
        
        isLoading = true
        defer { isLoading = false }
        
        let plan = Plan(
            matchID: matchID,
            proposerID: userID,
            receiverID: receiverID,
            activity: activity,
            location: location,
            locationName: locationName,
            locationLatitude: locationLatitude,
            locationLongitude: locationLongitude,
            proposedDates: proposedDates
        )

        do {
            try await plansService.createPlan(plan)
            await loadPlans() // Refresh
            return true
        } catch {
            errorMessage = "Failed to create plan: \(error.localizedDescription)"
            return false
        }
    }
    
    // MARK: - Confirm Plan
    
    @MainActor
    func confirmPlan(_ plan: Plan, selectedDate: Date) async -> Bool {
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await plansService.confirmPlan(planID: plan.id, selectedDate: selectedDate)
            await loadPlans()
            return true
        } catch {
            errorMessage = "Failed to confirm plan: \(error.localizedDescription)"
            return false
        }
    }
    
    // MARK: - Counter Propose
    
    @MainActor
    func counterPropose(_ plan: Plan, newDates: [Date]) async -> Bool {
        guard newDates.count == 3 else {
            errorMessage = "Must propose exactly 3 dates"
            return false
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await plansService.counterPropose(planID: plan.id, newDates: newDates)
            await loadPlans()
            return true
        } catch {
            errorMessage = "Failed to counter-propose: \(error.localizedDescription)"
            return false
        }
    }
    
    // MARK: - Decline Plan
    
    @MainActor
    func declinePlan(_ plan: Plan) async -> Bool {
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await plansService.declinePlan(planID: plan.id)
            await loadPlans()
            return true
        } catch {
            errorMessage = "Failed to decline plan: \(error.localizedDescription)"
            return false
        }
    }
    
    // MARK: - Cancel Plan
    
    @MainActor
    func cancelPlan(_ plan: Plan) async -> Bool {
        isLoading = true
        defer { isLoading = false }
        
        do {
            try await plansService.cancelPlan(planID: plan.id)
            await loadPlans()
            return true
        } catch {
            errorMessage = "Failed to cancel plan: \(error.localizedDescription)"
            return false
        }
    }
    
    // MARK: - Fetch User
    
    @MainActor
    func fetchUser(userID: String) async -> FirebaseUser? {
        do {
            return try await firestoreService.fetchUser(userID: userID)
        } catch {
            print("Failed to fetch user: \(error)")
            return nil
        }
    }
    
    // MARK: - Clear Error
    
    func clearError() {
        errorMessage = nil
    }
}
