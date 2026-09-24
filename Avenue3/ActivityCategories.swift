//
//  ActivityCategories.swift
//  Avenue3
//
//  Maps every predefined activity (see ActivitiesDatabase.swift) to a broad
//  category, so two users can match on "same kind of thing" (e.g. Basketball
//  + Football = both Sports & Fitness) even with zero identical activities.
//
//  This is a pure lookup table — no Firestore schema change, no migration.
//  Category is derived at match-comparison time from the activity name, not
//  stored on the user's document.
//
//  NOTE: "Football" and "Rucking" were added here (and must also be added to
//  ActivitiesDatabase.allActivities) — they weren't in the original 284.

import Foundation

enum ActivityCategory: String, CaseIterable {
    case sportsAndFitness
    case outdoorAndNature
    case foodAndDrink
    case artsAndCulture
    case entertainmentAndSocial
    case musicAndPerformance
    case learningAndEducation
    case hobbiesAndCrafts
    case wellnessAndSelfCare
    case travelAndAdventure
    case communityAndVolunteering
    case professionalAndBusiness
    case uniqueAndNiche

    var displayName: String {
        switch self {
        case .sportsAndFitness: return "Sports & Fitness"
        case .outdoorAndNature: return "Outdoor & Nature"
        case .foodAndDrink: return "Food & Drink"
        case .artsAndCulture: return "Arts & Culture"
        case .entertainmentAndSocial: return "Entertainment & Social"
        case .musicAndPerformance: return "Music & Performance"
        case .learningAndEducation: return "Learning & Education"
        case .hobbiesAndCrafts: return "Hobbies & Crafts"
        case .wellnessAndSelfCare: return "Wellness & Self-Care"
        case .travelAndAdventure: return "Travel & Adventure"
        case .communityAndVolunteering: return "Community & Volunteering"
        case .professionalAndBusiness: return "Professional & Business"
        case .uniqueAndNiche: return "Unique & Niche"
        }
    }

    /// Which suggestion pool this category belongs to — Today draws from `.spontaneous`
    /// categories first, Upcoming from `.planned` ones, so the two tabs' ghost-card
    /// suggestions come from disjoint pools instead of the same rotation. A static,
    /// hand-picked tier per category — no new Firestore field, no per-activity data.
    var suggestionTier: ActivitySuggestionTier {
        switch self {
        case .foodAndDrink, .entertainmentAndSocial, .wellnessAndSelfCare,
             .learningAndEducation, .hobbiesAndCrafts, .uniqueAndNiche:
            return .spontaneous
        case .sportsAndFitness, .outdoorAndNature, .artsAndCulture, .musicAndPerformance,
             .travelAndAdventure, .communityAndVolunteering, .professionalAndBusiness:
            return .planned
        }
    }
}

/// Today = "who's around right now?" (low-effort, can do with ~zero notice).
/// Upcoming = "plan something good." (bigger, worth scheduling ahead).
enum ActivitySuggestionTier {
    case spontaneous
    case planned
}

struct ActivityCategories {

    /// Exact activity name -> category. Keys must match
    /// ActivitiesDatabase.allActivities strings exactly (case-sensitive).
    static let map: [String: ActivityCategory] = [

        // MARK: - Sports & Fitness
        "Running": .sportsAndFitness,
        "Cycling": .sportsAndFitness,
        "Swimming": .sportsAndFitness,
        "Yoga": .sportsAndFitness,
        "Pilates": .sportsAndFitness,
        "CrossFit": .sportsAndFitness,
        "Skateboarding": .sportsAndFitness,
        "Basketball": .sportsAndFitness,
        "Tennis": .sportsAndFitness,
        "Volleyball": .sportsAndFitness,
        "Soccer": .sportsAndFitness,
        "Football": .sportsAndFitness, // NEW — not in original 284
        "Golf": .sportsAndFitness,
        "Bowling": .sportsAndFitness,
        "Boxing": .sportsAndFitness,
        "Martial Arts": .sportsAndFitness,
        "Dance": .sportsAndFitness,
        "Zumba": .sportsAndFitness,
        "Ice Skating": .sportsAndFitness,
        "Roller Skating": .sportsAndFitness,

        // MARK: - Outdoor & Nature
        // (includes items moved here from the original "Sports & Fitness"
        // comment block: Hiking, Rock Climbing, Bouldering, Surfing,
        // Kayaking, Paddleboarding, Sailing, Fishing, Horseback Riding,
        // Skiing, Snowboarding)
        "Hiking": .outdoorAndNature,
        "Rock Climbing": .outdoorAndNature,
        "Bouldering": .outdoorAndNature,
        "Surfing": .outdoorAndNature,
        "Kayaking": .outdoorAndNature,
        "Paddleboarding": .outdoorAndNature,
        "Sailing": .outdoorAndNature,
        "Fishing": .outdoorAndNature,
        "Horseback Riding": .outdoorAndNature,
        "Skiing": .outdoorAndNature,
        "Snowboarding": .outdoorAndNature,
        "Rucking": .outdoorAndNature, // NEW — not in original 284
        "Camping": .outdoorAndNature,
        "Backpacking": .outdoorAndNature,
        "National Parks": .outdoorAndNature,
        "State Parks": .outdoorAndNature,
        "Beach": .outdoorAndNature,
        "Lakes": .outdoorAndNature,
        "Mountains": .outdoorAndNature,
        "Desert": .outdoorAndNature,
        "Forest Bathing": .outdoorAndNature,
        "Bird Watching": .outdoorAndNature,
        "Wildlife Watching": .outdoorAndNature,
        "Star Gazing": .outdoorAndNature,
        "Sunsets": .outdoorAndNature,
        "Sunrise Watching": .outdoorAndNature,
        "Picnics": .outdoorAndNature,
        "Outdoor Concerts": .outdoorAndNature,
        "Festivals": .outdoorAndNature,
        "Flea Markets": .outdoorAndNature,
        "Botanical Gardens": .outdoorAndNature,
        "Arboretums": .outdoorAndNature,
        "Dog Parks": .outdoorAndNature,
        "Walking": .outdoorAndNature,
        "Jogging": .outdoorAndNature,
        "Trail Running": .outdoorAndNature,
        "Nature Photography": .outdoorAndNature,

        // MARK: - Food & Drink
        "Coffee": .foodAndDrink,
        "Brunch": .foodAndDrink,
        "Food Trucks": .foodAndDrink,
        "Cooking": .foodAndDrink,
        "Baking": .foodAndDrink,
        "Wine Tasting": .foodAndDrink,
        "Craft Beer": .foodAndDrink,
        "Cocktails": .foodAndDrink,
        "Tea": .foodAndDrink,
        "Vegetarian Food": .foodAndDrink,
        "Vegan Food": .foodAndDrink,
        "BBQ": .foodAndDrink,
        "Sushi": .foodAndDrink,
        "Pizza": .foodAndDrink,
        "Tacos": .foodAndDrink,
        "Burgers": .foodAndDrink,
        "Ramen": .foodAndDrink,
        "Thai Food": .foodAndDrink,
        "Mexican Food": .foodAndDrink,
        "Italian Food": .foodAndDrink,
        "Indian Food": .foodAndDrink,
        "Farmers Markets": .foodAndDrink,
        "Food Festivals": .foodAndDrink,
        "Brewery Tours": .foodAndDrink,
        "Restaurant Hopping": .foodAndDrink,

        // MARK: - Arts & Culture
        "Museums": .artsAndCulture,
        "Art Galleries": .artsAndCulture,
        "Photography": .artsAndCulture,
        "Painting": .artsAndCulture,
        "Drawing": .artsAndCulture,
        "Pottery": .artsAndCulture,
        "Sculpture": .artsAndCulture,
        "Street Art": .artsAndCulture,
        "Theater": .artsAndCulture,
        "Musical Theater": .artsAndCulture,
        "Opera": .artsAndCulture,
        "Ballet": .artsAndCulture,
        "Symphony": .artsAndCulture,
        "Jazz": .artsAndCulture,
        "Live Music": .artsAndCulture,
        "Concerts": .artsAndCulture,
        "Film": .artsAndCulture,
        "Independent Cinema": .artsAndCulture,
        "Film Festivals": .artsAndCulture,
        "Poetry": .artsAndCulture,
        "Writing": .artsAndCulture,
        "Reading": .artsAndCulture,
        "Book Clubs": .artsAndCulture,
        "Comic Books": .artsAndCulture,
        "Anime": .artsAndCulture,
        "Architecture": .artsAndCulture,
        "History": .artsAndCulture,
        "Antiques": .artsAndCulture,
        "Vintage Shopping": .artsAndCulture,
        "Crafts": .artsAndCulture,

        // MARK: - Entertainment & Social
        "Board Games": .entertainmentAndSocial,
        "Card Games": .entertainmentAndSocial,
        "Video Games": .entertainmentAndSocial,
        "Trivia": .entertainmentAndSocial,
        "Karaoke": .entertainmentAndSocial,
        "Stand-up Comedy": .entertainmentAndSocial,
        "Improv": .entertainmentAndSocial,
        "Escape Rooms": .entertainmentAndSocial,
        "Arcade Games": .entertainmentAndSocial,
        "Lazer Tag": .entertainmentAndSocial,
        "Mini Golf": .entertainmentAndSocial,
        "Axe Throwing": .entertainmentAndSocial,
        "Pool/Billiards": .entertainmentAndSocial,
        "Darts": .entertainmentAndSocial,
        "Bingo": .entertainmentAndSocial,
        "Casino": .entertainmentAndSocial,
        "Parties": .entertainmentAndSocial,
        "Networking Events": .entertainmentAndSocial,
        "Meetups": .entertainmentAndSocial,
        "Happy Hours": .entertainmentAndSocial,
        "Clubbing": .entertainmentAndSocial,
        "Bars": .entertainmentAndSocial,
        "Speakeasies": .entertainmentAndSocial,
        "Rooftop Bars": .entertainmentAndSocial,
        "Sports Bars": .entertainmentAndSocial,

        // MARK: - Music & Performance
        "Playing Guitar": .musicAndPerformance,
        "Playing Piano": .musicAndPerformance,
        "Playing Drums": .musicAndPerformance,
        "Singing": .musicAndPerformance,
        "Songwriting": .musicAndPerformance,
        "Music Production": .musicAndPerformance,
        "DJing": .musicAndPerformance,
        "Electronic Music": .musicAndPerformance,
        "Rock Music": .musicAndPerformance,
        "Hip Hop": .musicAndPerformance,
        "R&B": .musicAndPerformance,
        "Country Music": .musicAndPerformance,
        "Folk Music": .musicAndPerformance,
        "Classical Music": .musicAndPerformance,
        "Punk Rock": .musicAndPerformance,
        "Metal": .musicAndPerformance,
        "Indie Music": .musicAndPerformance,
        "Music Festivals": .musicAndPerformance,
        "Open Mic Nights": .musicAndPerformance,
        "Jam Sessions": .musicAndPerformance,

        // MARK: - Learning & Education
        "Language Learning": .learningAndEducation,
        "Spanish": .learningAndEducation,
        "French": .learningAndEducation,
        "Japanese": .learningAndEducation,
        "Coding": .learningAndEducation,
        "Web Development": .learningAndEducation,
        "App Development": .learningAndEducation,
        "Data Science": .learningAndEducation,
        "Machine Learning": .learningAndEducation,
        "Astronomy": .learningAndEducation,
        "Science": .learningAndEducation,
        "Philosophy": .learningAndEducation,
        "Psychology": .learningAndEducation,
        "Meditation": .learningAndEducation,
        "Mindfulness": .learningAndEducation,
        "Public Speaking": .learningAndEducation,
        "Podcasts": .learningAndEducation,
        "Documentaries": .learningAndEducation,
        "Online Courses": .learningAndEducation,
        "Workshops": .learningAndEducation,

        // MARK: - Hobbies & Crafts
        "Knitting": .hobbiesAndCrafts,
        "Crochet": .hobbiesAndCrafts,
        "Sewing": .hobbiesAndCrafts,
        "Quilting": .hobbiesAndCrafts,
        "Embroidery": .hobbiesAndCrafts,
        "Woodworking": .hobbiesAndCrafts,
        "Metalworking": .hobbiesAndCrafts,
        "Jewelry Making": .hobbiesAndCrafts,
        "Candle Making": .hobbiesAndCrafts,
        "Soap Making": .hobbiesAndCrafts,
        "Gardening": .hobbiesAndCrafts,
        "Urban Gardening": .hobbiesAndCrafts,
        "Houseplants": .hobbiesAndCrafts,
        "Succulents": .hobbiesAndCrafts,
        "Herb Gardens": .hobbiesAndCrafts,
        "DIY Projects": .hobbiesAndCrafts,
        "Home Improvement": .hobbiesAndCrafts,
        "Interior Design": .hobbiesAndCrafts,
        "Thrifting": .hobbiesAndCrafts,
        "Upcycling": .hobbiesAndCrafts,
        "Collecting": .hobbiesAndCrafts,
        "Model Building": .hobbiesAndCrafts,
        "Lego": .hobbiesAndCrafts,
        "Puzzles": .hobbiesAndCrafts,
        "Origami": .hobbiesAndCrafts,

        // MARK: - Wellness & Self-Care
        "Spa Days": .wellnessAndSelfCare,
        "Massage": .wellnessAndSelfCare,
        "Acupuncture": .wellnessAndSelfCare,
        "Reiki": .wellnessAndSelfCare,
        "Sound Healing": .wellnessAndSelfCare,
        "Breathwork": .wellnessAndSelfCare,
        "Journaling": .wellnessAndSelfCare,
        "Gratitude Practice": .wellnessAndSelfCare,
        "Self-Help": .wellnessAndSelfCare,
        "Personal Development": .wellnessAndSelfCare,
        "Life Coaching": .wellnessAndSelfCare,
        "Therapy": .wellnessAndSelfCare,
        "Support Groups": .wellnessAndSelfCare,
        "Nutrition": .wellnessAndSelfCare,
        "Meal Prep": .wellnessAndSelfCare,

        // MARK: - Travel & Adventure
        "Travel": .travelAndAdventure,
        "Road Trips": .travelAndAdventure,
        "Weekend Getaways": .travelAndAdventure,
        "International Travel": .travelAndAdventure,
        "Backpacking Abroad": .travelAndAdventure,
        "Hostels": .travelAndAdventure,
        "AirBnB Experiences": .travelAndAdventure,
        "City Breaks": .travelAndAdventure,
        "Beach Vacations": .travelAndAdventure,
        "Mountain Retreats": .travelAndAdventure,
        "Adventure Travel": .travelAndAdventure,
        "Solo Travel": .travelAndAdventure,
        "Group Travel": .travelAndAdventure,
        "Travel Photography": .travelAndAdventure,
        "Travel Vlogging": .travelAndAdventure,
        "Cultural Immersion": .travelAndAdventure,
        "Volunteer Travel": .travelAndAdventure,
        "Eco-Tourism": .travelAndAdventure,
        "Glamping": .travelAndAdventure,
        "Van Life": .travelAndAdventure,

        // MARK: - Community & Volunteering
        "Volunteering": .communityAndVolunteering,
        "Community Service": .communityAndVolunteering,
        "Animal Shelters": .communityAndVolunteering,
        "Food Banks": .communityAndVolunteering,
        "Habitat for Humanity": .communityAndVolunteering,
        "Environmental Cleanup": .communityAndVolunteering,
        "Mentoring": .communityAndVolunteering,
        "Tutoring": .communityAndVolunteering,
        "Coaching Youth": .communityAndVolunteering,
        "Political Activism": .communityAndVolunteering,
        "Social Justice": .communityAndVolunteering,
        "LGBTQ+ Events": .communityAndVolunteering,
        "Pride": .communityAndVolunteering,
        "Community Gardens": .communityAndVolunteering,
        "Neighborhood Events": .communityAndVolunteering,

        // MARK: - Professional & Business
        "Entrepreneurship": .professionalAndBusiness,
        "Startups": .professionalAndBusiness,
        "Investing": .professionalAndBusiness,
        "Stock Market": .professionalAndBusiness,
        "Crypto": .professionalAndBusiness,
        "Real Estate": .professionalAndBusiness,
        "Side Hustles": .professionalAndBusiness,
        "Freelancing": .professionalAndBusiness,
        "Consulting": .professionalAndBusiness,
        "Conferences": .professionalAndBusiness,
        "Professional Development": .professionalAndBusiness,
        "Leadership": .professionalAndBusiness,
        "Networking": .professionalAndBusiness,
        "Coworking": .professionalAndBusiness,

        // MARK: - Unique & Niche
        "Astrology": .uniqueAndNiche,
        "Tarot": .uniqueAndNiche,
        "Crystals": .uniqueAndNiche,
        "Spirituality": .uniqueAndNiche,
        "Ghost Hunting": .uniqueAndNiche,
        "Paranormal": .uniqueAndNiche,
        "UFOs": .uniqueAndNiche,
        "Conspiracy Theories": .uniqueAndNiche,
        "True Crime": .uniqueAndNiche,
        "Mystery Novels": .uniqueAndNiche,
        "Scavenger Hunts": .uniqueAndNiche,
        "Geocaching": .uniqueAndNiche,
        "Metal Detecting": .uniqueAndNiche,
        "Urban Exploration": .uniqueAndNiche,
        "Abandoned Places": .uniqueAndNiche,
        "Haunted Houses": .uniqueAndNiche,
        "Renaissance Fairs": .uniqueAndNiche,
        "Cosplay": .uniqueAndNiche,
        "LARPing": .uniqueAndNiche,
        "Medieval Combat": .uniqueAndNiche,
    ]

    /// Returns the category for an activity name, or nil if it's a
    /// user-typed custom activity not in the predefined list.
    static func category(for activityName: String) -> ActivityCategory? {
        return map[activityName]
    }
}
