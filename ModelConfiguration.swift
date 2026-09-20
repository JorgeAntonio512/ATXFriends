//
//  ModelConfiguration.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import SwiftData

/// Provides the SwiftData model container configuration for Avenue3
struct Avenue3ModelConfiguration {
    /// Creates and returns the SwiftData model container for the app
    static func createContainer() -> ModelContainer {
        let schema = Schema([
            User.self,
            ActivityModel.self,
            Match.self
        ])
        
        let modelConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        
        do {
            return try ModelContainer(for: schema, configurations: [modelConfiguration])
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
    }
    
    /// Creates an in-memory container for testing/previews
    static func createInMemoryContainer() -> ModelContainer {
        let schema = Schema([
            User.self,
            ActivityModel.self,
            Match.self
        ])
        
        let modelConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        
        do {
            return try ModelContainer(for: schema, configurations: [modelConfiguration])
        } catch {
            fatalError("Could not create in-memory ModelContainer: \(error)")
        }
    }
}
