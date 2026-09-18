// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function escapeSearchValue(value: string): string {
    return value.replace(/([\\"])/g, "\\$1");
}

export function presetSimulationSearch(presetName: string): string {
    return `preset:"${escapeSearchValue(presetName)}" -is:suspended`;
}

export function deckSimulationSearch(
    deckName: string,
    includeSubdecks: boolean,
): string {
    const escaped = escapeSearchValue(deckName);
    const currentDeck = `deck:"${escaped}"`;

    if (includeSubdecks) {
        return `${currentDeck} -is:suspended`;
    }

    return `${currentDeck} -deck:"${escaped}::*" -is:suspended`;
}
