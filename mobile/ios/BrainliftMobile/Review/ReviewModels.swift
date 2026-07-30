// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

struct ReviewDeck: Identifiable, Equatable, Sendable {
    let id: Int64
    let name: String
    let dueCount: UInt32
}

enum ReviewRating: String, CaseIterable, Identifiable, Sendable {
    case again = "Again"
    case hard = "Hard"
    case good = "Good"
    case easy = "Easy"

    var id: Self { self }

    var backendRating: Anki_Scheduler_CardAnswer.Rating {
        switch self {
        case .again: .again
        case .hard: .hard
        case .good: .good
        case .easy: .easy
        }
    }
}

struct ReviewSchedulingStates: Sendable {
    let again: Anki_Scheduler_SchedulingState
    let hard: Anki_Scheduler_SchedulingState
    let good: Anki_Scheduler_SchedulingState
    let easy: Anki_Scheduler_SchedulingState

    func state(for rating: ReviewRating) -> Anki_Scheduler_SchedulingState {
        switch rating {
        case .again: again
        case .hard: hard
        case .good: good
        case .easy: easy
        }
    }
}

struct ReviewCard: Identifiable, Sendable {
    let id: Int64
    let questionHTML: String
    let answerHTML: String
    let currentState: Anki_Scheduler_SchedulingState
    let states: ReviewSchedulingStates
    let customData: String
}

struct ReviewAnswer: Sendable {
    let cardID: Int64
    let currentState: Anki_Scheduler_SchedulingState
    let newState: Anki_Scheduler_SchedulingState
    let rating: ReviewRating
    let answeredAtMillis: Int64
    let millisecondsTaken: UInt32
}

enum ReviewPhase: Equatable, Sendable {
    case loading
    case choosingDeck
    case question
    case answer
    case finished
    case error
}

