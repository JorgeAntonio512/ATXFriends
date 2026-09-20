//
//  ActivitiesDatabase.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/7/26.
//

import Foundation

/// Comprehensive database of predefined activities
/// These activities are seeded into Firestore on first app launch
struct ActivitiesDatabase {
    
    /// All predefined activities organized by category
    static let allActivities: [String] = [
        // Sports & Fitness (30)
        "Running",
        "Hiking",
        "Cycling",
        "Swimming",
        "Yoga",
        "Pilates",
        "CrossFit",
        "Rock Climbing",
        "Bouldering",
        "Surfing",
        "Skateboarding",
        "Basketball",
        "Tennis",
        "Volleyball",
        "Soccer",
        "Golf",
        "Bowling",
        "Boxing",
        "Martial Arts",
        "Dance",
        "Zumba",
        "Skiing",
        "Snowboarding",
        "Ice Skating",
        "Roller Skating",
        "Kayaking",
        "Paddleboarding",
        "Sailing",
        "Fishing",
        "Horseback Riding",
        
        // Food & Drink (25)
        "Coffee",
        "Brunch",
        "Food Trucks",
        "Cooking",
        "Baking",
        "Wine Tasting",
        "Craft Beer",
        "Cocktails",
        "Tea",
        "Vegetarian Food",
        "Vegan Food",
        "BBQ",
        "Sushi",
        "Pizza",
        "Tacos",
        "Burgers",
        "Ramen",
        "Thai Food",
        "Mexican Food",
        "Italian Food",
        "Indian Food",
        "Farmers Markets",
        "Food Festivals",
        "Brewery Tours",
        "Restaurant Hopping",
        
        // Arts & Culture (30)
        "Museums",
        "Art Galleries",
        "Photography",
        "Painting",
        "Drawing",
        "Pottery",
        "Sculpture",
        "Street Art",
        "Theater",
        "Musical Theater",
        "Opera",
        "Ballet",
        "Symphony",
        "Jazz",
        "Live Music",
        "Concerts",
        "Film",
        "Independent Cinema",
        "Film Festivals",
        "Poetry",
        "Writing",
        "Reading",
        "Book Clubs",
        "Comic Books",
        "Anime",
        "Architecture",
        "History",
        "Antiques",
        "Vintage Shopping",
        "Crafts",
        
        // Entertainment & Social (25)
        "Board Games",
        "Card Games",
        "Video Games",
        "Trivia",
        "Karaoke",
        "Stand-up Comedy",
        "Improv",
        "Escape Rooms",
        "Arcade Games",
        "Lazer Tag",
        "Mini Golf",
        "Axe Throwing",
        "Pool/Billiards",
        "Darts",
        "Bingo",
        "Casino",
        "Parties",
        "Networking Events",
        "Meetups",
        "Happy Hours",
        "Clubbing",
        "Bars",
        "Speakeasies",
        "Rooftop Bars",
        "Sports Bars",
        
        // Outdoor & Nature (25)
        "Camping",
        "Backpacking",
        "National Parks",
        "State Parks",
        "Beach",
        "Lakes",
        "Mountains",
        "Desert",
        "Forest Bathing",
        "Bird Watching",
        "Wildlife Watching",
        "Star Gazing",
        "Sunsets",
        "Sunrise Watching",
        "Picnics",
        "Outdoor Concerts",
        "Festivals",
        "Flea Markets",
        "Botanical Gardens",
        "Arboretums",
        "Dog Parks",
        "Walking",
        "Jogging",
        "Trail Running",
        "Nature Photography",
        
        // Music & Performance (20)
        "Playing Guitar",
        "Playing Piano",
        "Playing Drums",
        "Singing",
        "Songwriting",
        "Music Production",
        "DJing",
        "Electronic Music",
        "Rock Music",
        "Hip Hop",
        "R&B",
        "Country Music",
        "Folk Music",
        "Classical Music",
        "Punk Rock",
        "Metal",
        "Indie Music",
        "Music Festivals",
        "Open Mic Nights",
        "Jam Sessions",
        
        // Learning & Education (20)
        "Language Learning",
        "Spanish",
        "French",
        "Japanese",
        "Coding",
        "Web Development",
        "App Development",
        "Data Science",
        "Machine Learning",
        "Astronomy",
        "Science",
        "Philosophy",
        "Psychology",
        "Meditation",
        "Mindfulness",
        "Public Speaking",
        "Podcasts",
        "Documentaries",
        "Online Courses",
        "Workshops",
        
        // Hobbies & Crafts (25)
        "Knitting",
        "Crochet",
        "Sewing",
        "Quilting",
        "Embroidery",
        "Woodworking",
        "Metalworking",
        "Jewelry Making",
        "Candle Making",
        "Soap Making",
        "Gardening",
        "Urban Gardening",
        "Houseplants",
        "Succulents",
        "Herb Gardens",
        "DIY Projects",
        "Home Improvement",
        "Interior Design",
        "Thrifting",
        "Upcycling",
        "Collecting",
        "Model Building",
        "Lego",
        "Puzzles",
        "Origami",
        
        // Wellness & Self-Care (15)
        "Spa Days",
        "Massage",
        "Acupuncture",
        "Reiki",
        "Sound Healing",
        "Breathwork",
        "Journaling",
        "Gratitude Practice",
        "Self-Help",
        "Personal Development",
        "Life Coaching",
        "Therapy",
        "Support Groups",
        "Nutrition",
        "Meal Prep",
        
        // Travel & Adventure (20)
        "Travel",
        "Road Trips",
        "Weekend Getaways",
        "International Travel",
        "Backpacking Abroad",
        "Hostels",
        "AirBnB Experiences",
        "City Breaks",
        "Beach Vacations",
        "Mountain Retreats",
        "Adventure Travel",
        "Solo Travel",
        "Group Travel",
        "Travel Photography",
        "Travel Vlogging",
        "Cultural Immersion",
        "Volunteer Travel",
        "Eco-Tourism",
        "Glamping",
        "Van Life",
        
        // Community & Volunteering (15)
        "Volunteering",
        "Community Service",
        "Animal Shelters",
        "Food Banks",
        "Habitat for Humanity",
        "Environmental Cleanup",
        "Mentoring",
        "Tutoring",
        "Coaching Youth",
        "Political Activism",
        "Social Justice",
        "LGBTQ+ Events",
        "Pride",
        "Community Gardens",
        "Neighborhood Events",
        
        // Professional & Business (14)
        "Entrepreneurship",
        "Startups",
        "Investing",
        "Stock Market",
        "Crypto",
        "Real Estate",
        "Side Hustles",
        "Freelancing",
        "Consulting",
        "Conferences",
        "Professional Development",
        "Leadership",
        "Networking",
        "Coworking",
        
        // Unique & Niche (20)
        "Astrology",
        "Tarot",
        "Crystals",
        "Spirituality",
        "Ghost Hunting",
        "Paranormal",
        "UFOs",
        "Conspiracy Theories",
        "True Crime",
        "Mystery Novels",
        "Scavenger Hunts",
        "Geocaching",
        "Metal Detecting",
        "Urban Exploration",
        "Abandoned Places",
        "Haunted Houses",
        "Renaissance Fairs",
        "Cosplay",
        "LARPing",
        "Medieval Combat"
    ].sorted() // Alphabetically sorted for easy browsing
    
    /// Creates Activity objects from the database
    /// - Parameter isUserAdded: Whether these are user-added (false for seed data)
    /// - Returns: Array of Activity objects
    static func createActivities(isUserAdded: Bool = false) -> [Activity] {
        return allActivities.map { name in
            Activity(
                id: UUID().uuidString,
                name: name,
                isUserAdded: isUserAdded,
                createdAt: Date()
            )
        }
    }
    
    /// Returns activities filtered by search text
    /// - Parameter searchText: The search query
    /// - Returns: Filtered list of activity names
    static func search(_ searchText: String) -> [String] {
        guard !searchText.isEmpty else {
            return allActivities
        }
        
        return allActivities.filter { activity in
            activity.localizedCaseInsensitiveContains(searchText)
        }
    }
    
    /// Checks if an activity exists in the database
    /// - Parameter name: The activity name to check
    /// - Returns: True if the activity exists
    static func contains(_ name: String) -> Bool {
        return allActivities.contains { activity in
            activity.localizedCaseInsensitiveCompare(name) == .orderedSame
        }
    }
    
    /// Total number of predefined activities
    static var count: Int {
        return allActivities.count
    }
}
