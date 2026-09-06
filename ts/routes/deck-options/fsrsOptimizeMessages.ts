// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";

export function parametersEqual(
    left: number[],
    right: number[],
): boolean {
    return left.every((n, i) => n.toFixed(4) === right[i]?.toFixed(4));
}

export function getFsrsAlreadyOptimalMessage(
    alreadyOptimal: boolean,
    isDefault: boolean,
    fsrsItems: number | undefined,
    revlogCount: number | undefined,
): string {
    console.log("getFsrsAlreadyOptimalMessage", alreadyOptimal, isDefault, fsrsItems, revlogCount);
    if (alreadyOptimal) {
        if (fsrsItems) {
            return isDefault
                ? tr.deckConfigFsrsParamsUsingDefault()
                : tr.deckConfigFsrsParamsOptimal();

        } else {
            return revlogCount
                ? tr.deckConfigFsrsReviewsIgnoredByOptimizer()
                : tr.deckConfigFsrsParamsNoReviews();
        }
    } else {
        return "";
    }
}
