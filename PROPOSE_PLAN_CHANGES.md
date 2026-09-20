# Propose a Plan Feature - Implementation Summary

This document summarizes the changes made to implement the "Propose a Plan" feature that allows users to tap a button on a connected match to open CreatePlanView with that match already selected.

## Changes Made

### Step 1: CreatePlanView.swift

**Added preselected match support:**
- Added `var preselectedMatch: Match? = nil` stored property above `@State private var selectedMatch: Match?`
- Modified `.task` modifier to handle preselected match:
  ```swift
  .task {
      await loadMatches()
      await loadActivities()
      if let preselected = preselectedMatch {
          selectedMatch = preselected
          loadSharedActivities(for: preselected)
      }
  }
  ```

### Step 2: MatchDetailView.swift

**Added "Propose a Plan" button for connected matches:**
- Added `@State private var showProposePlan = false` state variable
- Added new button in the `if matchWithUser.isMutual` section, below the "Send Message" button:
  ```swift
  Button {
      showProposePlan = true
  } label: {
      HStack(spacing: 8) {
          Image(systemName: "calendar.badge.plus")
              .font(.system(size: 18))
          Text("Propose a Plan")
              .font(.system(size: 18, weight: .semibold, design: .rounded))
      }
      .foregroundColor(Color(red: 0.45, green: 0.60, blue: 0.50))
      .frame(maxWidth: .infinity)
      .frame(height: 56)
      .background(Color.white.opacity(0.9))
      .cornerRadius(16)
      .overlay(
          RoundedRectangle(cornerRadius: 16)
              .strokeBorder(Color(red: 0.45, green: 0.60, blue: 0.50).opacity(0.4), lineWidth: 1.5)
      )
  }
  ```
- Added sheet modifier to present CreatePlanView:
  ```swift
  .sheet(isPresented: $showProposePlan) {
      CreatePlanView(
          viewModel: PlansViewModel(),
          preselectedMatch: matchWithUser.match
      )
  }
  ```

### Step 3: MatchesView.swift

**Updated ConnectedMatchRow:**
- Added `let onPlan: () -> Void` closure parameter to `ConnectedMatchRow`
- Added calendar button next to the message button:
  ```swift
  // Plan button
  Button(action: onPlan) {
      Image(systemName: "calendar.badge.plus")
          .font(.system(size: 20))
          .foregroundColor(Color(red: 0.45, green: 0.60, blue: 0.50))
          .frame(width: 44, height: 44)
          .background(Color(red: 0.55, green: 0.70, blue: 0.55).opacity(0.15))
          .clipShape(Circle())
  }
  .buttonStyle(.plain)
  ```

**Updated MatchesView:**
- Added `@State private var planTarget: MatchWithUser?` state variable
- Added `handlePlan` function:
  ```swift
  private func handlePlan(_ matchWithUser: MatchWithUser) {
      planTarget = matchWithUser
  }
  ```
- Updated `ConnectedMatchRow` call to include `onPlan` closure:
  ```swift
  ConnectedMatchRow(
      matchWithUser: matchWithUser,
      onMessage: {
          handleMessage(matchWithUser)
      },
      onPlan: {
          handlePlan(matchWithUser)
      },
      onTap: {
          selectedMatch = matchWithUser
      }
  )
  ```
- Added sheet modifier to present CreatePlanView:
  ```swift
  .sheet(item: $planTarget) { matchWithUser in
      CreatePlanView(
          viewModel: PlansViewModel(),
          preselectedMatch: matchWithUser.match
      )
  }
  ```

## User Experience Flow

1. **From MatchesView:** User sees a calendar icon button next to the message button on connected match rows
2. **From MatchDetailView:** User sees a "Propose a Plan" button below the "Send Message" button for mutual matches
3. **Either button:** Opens CreatePlanView with the match already selected
4. **CreatePlanView:** The selected match is pre-filled, shared activities are loaded, and smart dates are calculated based on overlapping availability

## Testing Checklist

- [ ] Tap calendar button in MatchesView connected match row → CreatePlanView opens with match selected
- [ ] Tap "Propose a Plan" in MatchDetailView → CreatePlanView opens with match selected
- [ ] Verify shared activities are loaded when match is preselected
- [ ] Verify smart dates are calculated based on overlapping time slots
- [ ] Verify CreatePlanView still works normally when opened without a preselected match
- [ ] Verify UI styling matches the app's design system

## Design Notes

- Calendar icon button uses a subtle background (`opacity(0.15)`) to distinguish it from the primary message button
- "Propose a Plan" button in detail view uses an outlined style to create visual hierarchy below the primary "Send Message" button
- Both entry points use the same `calendar.badge.plus` SF Symbol for consistency
