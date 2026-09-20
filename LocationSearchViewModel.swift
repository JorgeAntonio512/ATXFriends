//
//  LocationSearchViewModel.swift
//  Avenue3
//

import CoreLocation
import MapKit

/// Live location search for the Plan location picker. Uses MKLocalSearchCompleter for
/// live-typing suggestions, then resolves each into a full MKMapItem via MKLocalSearch to get
/// real coordinates. Results are explicitly sorted by distance from the user's stored
/// coordinates before display — MapKit's own completer/search result order is relevance-based,
/// not guaranteed to be distance-sorted.
@Observable
final class LocationSearchViewModel: NSObject, MKLocalSearchCompleterDelegate {
    struct Result: Identifiable {
        let id = UUID()
        let name: String
        let address: String?
        let coordinate: CLLocationCoordinate2D
        let distanceMiles: Double?
    }

    /// Explicit state so a consuming field is never silently blank while searching.
    enum SearchState: Equatable {
        case idle
        case searching
        case hasResults
        case noMatches
        case error
    }

    private let completer = MKLocalSearchCompleter()
    private var userCoordinate: CLLocationCoordinate2D?
    private var resolveTask: Task<Void, Never>?

    private(set) var results: [Result] = []
    private(set) var state: SearchState = .idle

    var queryFragment: String = "" {
        didSet {
            guard queryFragment != oldValue else { return }
            print("🔎 [LocationSearch] queryFragment didSet: \"\(oldValue)\" -> \"\(queryFragment)\" (thread: \(Thread.isMainThread ? "main" : "background"))")
            if queryFragment.isEmpty {
                resolveTask?.cancel()
                results = []
                state = .idle
            } else {
                state = .searching
            }
            completer.queryFragment = queryFragment
            print("🔎 [LocationSearch] handed to MKLocalSearchCompleter: \"\(completer.queryFragment)\" resultTypes=\(completer.resultTypes) region=\(String(describing: completer.region))")
        }
    }

    /// - Parameter userCoordinate: the user's signup-time stored coordinates (same ones the
    ///   geofencing gate captured). Used to bias the search region and to sort results by
    ///   distance. Pass nil (or the app's (0, 0) "unset" sentinel) to search with no bias.
    init(userCoordinate: CLLocationCoordinate2D?) {
        self.userCoordinate = nil
        super.init()
        completer.delegate = self
        completer.resultTypes = [.pointOfInterest, .address]
        setUserCoordinate(userCoordinate)
        print("🔎 [LocationSearch] init, delegate set to \(ObjectIdentifier(self)), userCoordinate=\(String(describing: userCoordinate))")
    }

    /// Updates the coordinate used to bias the search region and sort results — call once the
    /// current user's stored coordinates finish loading (e.g. after the view's `.task` fetch).
    func setUserCoordinate(_ coordinate: CLLocationCoordinate2D?) {
        userCoordinate = coordinate
        guard let coordinate, coordinate.latitude != 0 || coordinate.longitude != 0 else {
            print("🔎 [LocationSearch] setUserCoordinate: nil/zero, no region bias set")
            return
        }
        completer.region = MKCoordinateRegion(
            center: coordinate,
            latitudinalMeters: 20_000,
            longitudinalMeters: 20_000
        )
        print("🔎 [LocationSearch] setUserCoordinate: region biased to \(coordinate)")
    }

    func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        print("🔎 [LocationSearch] completerDidUpdateResults FIRED (thread: \(Thread.isMainThread ? "main" : "background")) rawCount=\(completer.results.count) titles=\(completer.results.prefix(8).map { $0.title })")
        // Resolve more than the final 5 shown — some completions fail to resolve (ambiguous
        // query completions, transient errors), and resolution order isn't distance order.
        let completions = Array(completer.results.prefix(8))
        // MKLocalSearchCompleterDelegate does not document a main-thread guarantee; hop
        // explicitly so @Observable state mutations always land where SwiftUI expects them.
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.resolveTask?.cancel()
            if completions.isEmpty {
                self.state = .noMatches
                self.results = []
                return
            }
            self.state = .searching
            self.resolveTask = Task { [weak self] in
                await self?.resolve(completions)
            }
        }
    }

    func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        print("🔎 [LocationSearch] completer didFailWithError: \(error)")
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.resolveTask?.cancel()
            self.results = []
            self.state = .error
        }
    }

    @MainActor
    private func resolve(_ completions: [MKLocalSearchCompletion]) async {
        let userCoordinate = self.userCoordinate

        let resolved = await withTaskGroup(of: Result?.self) { group in
            for completion in completions {
                group.addTask {
                    let request = MKLocalSearch.Request(completion: completion)
                    guard let item = try? await MKLocalSearch(request: request).start().mapItems.first else {
                        return nil
                    }
                    let coordinate = item.placemark.coordinate
                    let distanceMiles: Double? = userCoordinate.flatMap { user in
                        guard user.latitude != 0 || user.longitude != 0 else { return nil }
                        return CLLocation(latitude: user.latitude, longitude: user.longitude)
                            .distance(from: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)) / 1609.34
                    }
                    return Result(
                        name: item.name ?? completion.title,
                        address: LocationSearchViewModel.addressLine(for: item),
                        coordinate: coordinate,
                        distanceMiles: distanceMiles
                    )
                }
            }

            var collected: [Result] = []
            for await result in group {
                if let result { collected.append(result) }
            }
            return collected
        }

        guard !Task.isCancelled else {
            print("🔎 [LocationSearch] resolve: task cancelled, dropping \(resolved.count) resolved items")
            return
        }

        // Explicitly sort by distance rather than trusting resolution order.
        let sorted = resolved.sorted { lhs, rhs in
            switch (lhs.distanceMiles, rhs.distanceMiles) {
            case let (l?, r?): return l < r
            case (nil, _): return false
            case (_, nil): return true
            }
        }
        results = Array(sorted.prefix(5))
        state = results.isEmpty ? .noMatches : .hasResults
        print("🔎 [LocationSearch] resolve: published \(results.count) results (resolved \(resolved.count) of \(completions.count) completions) on thread \(Thread.isMainThread ? "main" : "background")")
    }

    private static func addressLine(for item: MKMapItem) -> String? {
        let placemark = item.placemark
        let parts = [placemark.thoroughfare, placemark.locality].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }
}
