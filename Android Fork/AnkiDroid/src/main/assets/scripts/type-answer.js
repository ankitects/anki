// SPDX-License-Identifier: GPL-3.0-or-later

// Keep nosuggest scoped to the marked input, including when a card has other editable fields.
// A new document must not inherit the previous card's keyboard state.
AnkiDroidKeyboard.setNoSuggest(false);

// Capture before card scripts can stop propagation. On focusout, use the destination
// so moving between marked fields does not temporarily restore suggestions.
document.addEventListener(
    "focusin",
    event => {
        AnkiDroidKeyboard.setNoSuggest(event.target.dataset.ankidroidNosuggest === "true");
    },
    true,
);
document.addEventListener(
    "focusout",
    event => {
        AnkiDroidKeyboard.setNoSuggest(event.relatedTarget?.dataset?.ankidroidNosuggest === "true");
    },
    true,
);
