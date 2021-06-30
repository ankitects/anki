// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "lib/i18n";

export const reviewMixChoices = (): string[] => [
    tr.deckConfigReviewMixMixWithReviews(),
    tr.deckConfigReviewMixShowAfterReviews(),
    tr.deckConfigReviewMixShowBeforeReviews(),
];
