// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    DeckConfig_Config_AnswerAction,
    DeckConfig_Config_LeechAction,
    DeckConfig_Config_NewCardGatherPriority,
    DeckConfig_Config_NewCardInsertOrder,
    DeckConfig_Config_NewCardSortOrder,
    DeckConfig_Config_QuestionAction,
    DeckConfig_Config_ReviewCardOrder,
    DeckConfig_Config_ReviewMix,
} from "@generated/anki/deck_config_pb";
import * as tr from "@generated/ftl";

import type { Choice } from "$lib/components/EnumSelector.svelte";

export function newGatherPriorityChoices(): Choice<DeckConfig_Config_NewCardGatherPriority>[] {
    return [
        {
            label: tr.deckConfigNewGatherPriorityDeck(),
            value: DeckConfig_Config_NewCardGatherPriority.DECK,
        },
        {
            label: tr.deckConfigNewGatherPriorityDeckThenRandomNotes(),
            value: DeckConfig_Config_NewCardGatherPriority.DECK_THEN_RANDOM_NOTES,
        },
        {
            label: tr.deckConfigNewGatherPriorityPositionLowestFirst(),
            value: DeckConfig_Config_NewCardGatherPriority.LOWEST_POSITION,
        },
        {
            label: tr.deckConfigNewGatherPriorityPositionHighestFirst(),
            value: DeckConfig_Config_NewCardGatherPriority.HIGHEST_POSITION,
        },
        {
            label: tr.deckConfigNewGatherPriorityRandomNotes(),
            value: DeckConfig_Config_NewCardGatherPriority.RANDOM_NOTES,
        },
        {
            label: tr.deckConfigNewGatherPriorityRandomCards(),
            value: DeckConfig_Config_NewCardGatherPriority.RANDOM_CARDS,
        },
    ];
}

export function newSortOrderChoices(): Choice<DeckConfig_Config_NewCardSortOrder>[] {
    return [
        {
            label: tr.deckConfigSortOrderTemplateThenGather(),
            value: DeckConfig_Config_NewCardSortOrder.TEMPLATE,
        },
        {
            label: tr.deckConfigSortOrderGather(),
            value: DeckConfig_Config_NewCardSortOrder.NO_SORT,
        },
        {
            label: tr.deckConfigSortOrderCardTemplateThenRandom(),
            value: DeckConfig_Config_NewCardSortOrder.TEMPLATE_THEN_RANDOM,
        },
        {
            label: tr.deckConfigSortOrderRandomNoteThenTemplate(),
            value: DeckConfig_Config_NewCardSortOrder.RANDOM_NOTE_THEN_TEMPLATE,
        },
        {
            label: tr.deckConfigSortOrderRandom(),
            value: DeckConfig_Config_NewCardSortOrder.RANDOM_CARD,
        },
    ];
}

export function reviewOrderChoices(fsrs: boolean): Choice<DeckConfig_Config_ReviewCardOrder>[] {
    return [
        ...[
            {
                label: tr.deckConfigSortOrderDueDateThenRandom(),
                value: DeckConfig_Config_ReviewCardOrder.DAY,
            },
            {
                label: tr.deckConfigSortOrderDueDateThenDeck(),
                value: DeckConfig_Config_ReviewCardOrder.DAY_THEN_DECK,
            },
            {
                label: tr.deckConfigSortOrderDeckThenDueDate(),
                value: DeckConfig_Config_ReviewCardOrder.DECK_THEN_DAY,
            },
            {
                label: tr.deckConfigSortOrderAscendingIntervals(),
                value: DeckConfig_Config_ReviewCardOrder.INTERVALS_ASCENDING,
            },
            {
                label: tr.deckConfigSortOrderDescendingIntervals(),
                value: DeckConfig_Config_ReviewCardOrder.INTERVALS_DESCENDING,
            },
        ],
        ...difficultyOrders(fsrs),
        ...[
            {
                label: tr.deckConfigSortOrderRetrievabilityAscending(),
                value: DeckConfig_Config_ReviewCardOrder.RETRIEVABILITY_ASCENDING,
            },
            {
                label: tr.deckConfigSortOrderRetrievabilityDescending(),
                value: DeckConfig_Config_ReviewCardOrder.RETRIEVABILITY_DESCENDING,
            },
            {
                label: tr.deckConfigSortOrderRandom(),
                value: DeckConfig_Config_ReviewCardOrder.RANDOM,
            },
            {
                label: tr.decksOrderAdded(),
                value: DeckConfig_Config_ReviewCardOrder.ADDED,
            },
            {
                label: tr.decksLatestAddedFirst(),
                value: DeckConfig_Config_ReviewCardOrder.REVERSE_ADDED,
            },
        ],
    ];
}

export function reviewMixChoices(): Choice<DeckConfig_Config_ReviewMix>[] {
    return [
        {
            label: tr.deckConfigReviewMixMixWithReviews(),
            value: DeckConfig_Config_ReviewMix.MIX_WITH_REVIEWS,
        },
        {
            label: tr.deckConfigReviewMixShowAfterReviews(),
            value: DeckConfig_Config_ReviewMix.AFTER_REVIEWS,
        },
        {
            label: tr.deckConfigReviewMixShowBeforeReviews(),
            value: DeckConfig_Config_ReviewMix.BEFORE_REVIEWS,
        },
    ];
}

export function leechChoices(): Choice<DeckConfig_Config_LeechAction>[] {
    return [
        {
            label: tr.actionsSuspendCard(),
            value: DeckConfig_Config_LeechAction.SUSPEND,
        },
        {
            label: tr.schedulingTagOnly(),
            value: DeckConfig_Config_LeechAction.TAG_ONLY,
        },
    ];
}

export function newInsertOrderChoices(): Choice<DeckConfig_Config_NewCardInsertOrder>[] {
    return [
        {
            label: tr.deckConfigNewInsertionOrderSequential(),
            value: DeckConfig_Config_NewCardInsertOrder.DUE,
        },
        {
            label: tr.deckConfigNewInsertionOrderRandom(),
            value: DeckConfig_Config_NewCardInsertOrder.RANDOM,
        },
    ];
}

export function answerChoices(): Choice<DeckConfig_Config_AnswerAction>[] {
    return [
        {
            label: tr.studyingBuryCard(),
            value: DeckConfig_Config_AnswerAction.BURY_CARD,
        },
        {
            label: tr.deckConfigAnswerAgain(),
            value: DeckConfig_Config_AnswerAction.ANSWER_AGAIN,
        },
        {
            label: tr.deckConfigAnswerGood(),
            value: DeckConfig_Config_AnswerAction.ANSWER_GOOD,
        },
        {
            label: tr.deckConfigAnswerHard(),
            value: DeckConfig_Config_AnswerAction.ANSWER_HARD,
        },
        {
            label: tr.deckConfigShowReminder(),
            value: DeckConfig_Config_AnswerAction.SHOW_REMINDER,
        },
    ];
}
export function questionActionChoices(): Choice<DeckConfig_Config_QuestionAction>[] {
    return [
        {
            label: tr.deckConfigQuestionActionShowAnswer(),
            value: DeckConfig_Config_QuestionAction.SHOW_ANSWER,
        },
        {
            label: tr.deckConfigQuestionActionShowReminder(),
            value: DeckConfig_Config_QuestionAction.SHOW_REMINDER,
        },
    ];
}

export const DEFAULT_CMRR_TARGET = "memorized";

export function CMRRTargetChoices(): Choice<string>[] {
    return [
        {
            label: "Memorized (Default)",
            value: "memorized",
        },
        {
            label: "Stability (Experimental)",
            value: "stability",
        },
    ] as const;
}

function difficultyOrders(fsrs: boolean): Choice<DeckConfig_Config_ReviewCardOrder>[] {
    const order = [
        {
            label: fsrs ? tr.deckConfigSortOrderDescendingDifficulty() : tr.deckConfigSortOrderAscendingEase(),
            value: DeckConfig_Config_ReviewCardOrder.EASE_ASCENDING,
        },
        {
            label: fsrs ? tr.deckConfigSortOrderAscendingDifficulty() : tr.deckConfigSortOrderDescendingEase(),
            value: DeckConfig_Config_ReviewCardOrder.EASE_DESCENDING,
        },
    ];
    if (fsrs) {
        order.reverse();
    }
    return order;
}
