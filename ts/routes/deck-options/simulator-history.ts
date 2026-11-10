// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/** A snapshot of a finished FSRS simulation. Changing the controls afterwards
 * does not change the recorded run or its export.
 */
export interface SimulationSettings {
    daysToSimulate: number;
    deckSize: number;
    desiredRetention: number;
    newLimit: number;
    reviewLimit: number;
}

export interface SimulationRun extends SimulationSettings {
    number: number;
    dailyNewCount: number[];
    dailyReviewCount: number[];
}

export function recordSimulation(
    number: number,
    settings: SimulationSettings,
    dailyNewCount: readonly number[],
    dailyReviewCount: readonly number[],
): SimulationRun {
    return {
        number,
        daysToSimulate: settings.daysToSimulate,
        deckSize: settings.deckSize,
        desiredRetention: settings.desiredRetention,
        newLimit: settings.newLimit,
        reviewLimit: settings.reviewLimit,
        dailyNewCount: [...dailyNewCount],
        dailyReviewCount: [...dailyReviewCount],
    };
}

export function simulationTotals(run: SimulationRun) {
    const newCards = run.dailyNewCount.reduce((sum, count) => sum + count, 0);
    const reviews = run.dailyReviewCount.reduce((sum, count) => sum + count, 0);
    return { newCards, reviews, total: newCards + reviews };
}

/** One row per simulated day, per run; no graph smoothing is applied. */
export function simulationHistoryCsv(runs: readonly SimulationRun[]): string {
    const lines = [
        "run,day,new,reviews,total,desired_retention,new_limit,review_limit,additional_new_cards,days_to_simulate",
    ];
    for (const run of runs) {
        const days = Math.max(run.dailyNewCount.length, run.dailyReviewCount.length);
        for (let day = 0; day < days; day++) {
            const newCards = run.dailyNewCount[day] ?? 0;
            const reviews = run.dailyReviewCount[day] ?? 0;
            lines.push([
                run.number,
                day + 1,
                newCards,
                reviews,
                newCards + reviews,
                run.desiredRetention,
                run.newLimit,
                run.reviewLimit,
                run.deckSize,
                run.daysToSimulate,
            ].join(","));
        }
    }
    return lines.join("\r\n") + "\r\n";
}
