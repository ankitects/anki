"use strict";
globalThis.ankidroid = globalThis.ankidroid || {};

globalThis.ankidroid.userAction = function (number) {
    try {
        let userJs = globalThis[`userJs${number}`];
        if (userJs != null) {
            userJs();
        } else {
            window.location.href = `missing-user-action:${number}`;
        }
    } catch (e) {
        alert(e);
    }
};

globalThis.ankidroid.showHint = function () {
    document.querySelector("a.hint:not([style*='display: none'])")?.click();
};

globalThis.ankidroid.showAllHints = function () {
    document.querySelectorAll("a.hint").forEach(el => el.click());
};

/**
 * @param {KeyboardEvent} event - the onkeydown event of the type answer <input>
 */
globalThis.ankidroid.onTypeAnswerKeyDown = function (event) {
    if (event.key === "Enter") {
        window.location.href = `ankidroid://show-answer`;
    }
};

// ============================================================================
// Input focus listeners
// ============================================================================

(() => {
    function localRequest(endpoint, payload = {}) {
        fetch(`ankidroid/${endpoint}`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(payload),
        }).catch(err => console.log("Failed to reach local server:", err));
    }

    /**
     * Checks if the target element is a text input field.
     * @param {Event} event
     * @returns {boolean}
     */
    function isTextInput(event) {
        const target = event.target;
        return target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA");
    }

    document.addEventListener("focusin", event => {
        if (isTextInput(event)) {
            localRequest("focusin");
        }
    });

    document.addEventListener("focusout", event => {
        if (isTextInput(event)) {
            localRequest("focusout");
        }
    });
})();

// ============================================================================
// Gesture detection
// ============================================================================

(() => {
    const SCHEME = "gesture";
    const MULTI_TOUCH_TIMEOUT = 300;
    const GESTURE_TIMEOUT = 800;

    let startX = 0,
        startY = 0,
        touchCount = 0,
        touchStartTime = 0;

    document.addEventListener(
        "touchstart",
        event => {
            touchCount = event.touches.length;
            startX = event.touches[0].pageX;
            startY = event.touches[0].pageY;
            // start counting from the first finger touch
            if (touchCount === 1) {
                touchStartTime = Date.now();
            }
        },
        { passive: true },
    );

    document.addEventListener(
        "touchend",
        event => {
            // Only process after the final finger is lifted
            if (
                event.touches.length > 0 ||
                touchCount > 4 ||
                isTextSelected() ||
                isInteractable(event)
            )
                return;

            // Multi-finger detection
            if (touchCount > 1) {
                if (Date.now() - touchStartTime > MULTI_TOUCH_TIMEOUT) {
                    return;
                }
                window.location.href = `${SCHEME}://multiFingerTap/?touchCount=${touchCount}`;
                return;
            }

            // Ignore gesture if it takes too long
            if (Date.now() - touchStartTime > GESTURE_TIMEOUT) {
                return;
            }

            // Swipes and tap detection
            const endX = event.changedTouches[0].pageX;
            const endY = event.changedTouches[0].pageY;
            const scrollDirection = getScrollDirection(event.target);
            const params = new URLSearchParams({
                x: Math.round(endX),
                y: Math.round(endY),
                deltaX: Math.round(endX - startX),
                deltaY: Math.round(endY - startY),
                time: Date.now(),
            });
            if (scrollDirection !== null) {
                params.append("scrollDirection", scrollDirection);
            }
            window.location.href = `${SCHEME}://tapOrSwipe/?${params.toString()}`;
        },
        { passive: true },
    );

    /**
     * Checks if the target element or its parents are interactive.
     * @param {TouchEvent} event
     * @returns {boolean}
     */
    function isInteractable(event) {
        let node = event.target;
        while (node && node !== document) {
            if (
                node.nodeName === "A" ||
                node.onclick ||
                node.nodeName === "BUTTON" ||
                node.nodeName === "VIDEO" ||
                node.nodeName === "SUMMARY" ||
                node.nodeName === "INPUT" ||
                node.getAttribute("contentEditable") ||
                (node.classList && node.classList.contains("tappable"))
            ) {
                return true;
            }
            node = node.parentNode;
        }
        return false;
    }

    /**
     * Checks if the user is selecting text.
     * @returns {boolean}
     */
    function isTextSelected() {
        return !document.getSelection().isCollapsed;
    }

    /**
     * Checks if an element or its parents are scrollable and returns the direction(s).
     * It traverses up the DOM from the event target, checking the first scrollable ancestor.
     * @param {HTMLElement} target - The element where the touch ended.
     * @returns {'h'|'v'|'hv'|null} - The scroll direction(s) if found, otherwise null.
     */
    function getScrollDirection(target) {
        let node = target;
        while (node && node.nodeType === Node.ELEMENT_NODE) {
            const style = window.getComputedStyle(node);

            const isHorizontallyScrollable =
                (style.overflowX === "auto" || style.overflowX === "scroll") &&
                node.scrollWidth > node.clientWidth;

            const isVerticallyScrollable =
                (style.overflowY === "auto" || style.overflowY === "scroll") &&
                node.scrollHeight > node.clientHeight;

            if (isHorizontallyScrollable && isVerticallyScrollable) {
                return "hv";
            }
            if (isHorizontallyScrollable) {
                return "h";
            }
            if (isVerticallyScrollable) {
                return "v";
            }
            node = node.parentNode;
        }
        // No scrollable parent was found.
        return null;
    }
})();

// region TEMPORARY
// those functions should be removed once the new API is implemented.
// they only exist in the new study screen so the user is warned that they are not supported.

function taFocus() {
    window.location.href = "signal:typefocus";
}

function showAnswer() {
    window.location.href = "ankidroid://show-answer";
}
function buttonAnswerEase1() {
    window.location.href = "signal:answer_ease1";
}
function buttonAnswerEase2() {
    window.location.href = "signal:answer_ease2";
}
function buttonAnswerEase3() {
    window.location.href = "signal:answer_ease3";
}
function buttonAnswerEase4() {
    window.location.href = "signal:answer_ease4";
}

function reloadPage() {
    window.location.href = "signal:reload_card_html";
}

const jsApiList = {
    ankiGetNewCardCount: "newCardCount",
    ankiGetLrnCardCount: "lrnCardCount",
    ankiGetRevCardCount: "revCardCount",
    ankiGetETA: "eta",
    ankiGetCardMark: "cardMark",
    ankiGetCardFlag: "cardFlag",
    ankiGetNextTime1: "nextTime1",
    ankiGetNextTime2: "nextTime2",
    ankiGetNextTime3: "nextTime3",
    ankiGetNextTime4: "nextTime4",
    ankiGetCardReps: "cardReps",
    ankiGetCardInterval: "cardInterval",
    ankiGetCardFactor: "cardFactor",
    ankiGetCardMod: "cardMod",
    ankiGetCardId: "cardId",
    ankiGetCardNid: "cardNid",
    ankiGetCardType: "cardType",
    ankiGetCardDid: "cardDid",
    ankiGetCardLeft: "cardLeft",
    ankiGetCardODid: "cardODid",
    ankiGetCardODue: "cardODue",
    ankiGetCardQueue: "cardQueue",
    ankiGetCardLapses: "cardLapses",
    ankiGetCardDue: "cardDue",
    ankiIsInFullscreen: "isInFullscreen",
    ankiIsTopbarShown: "isTopbarShown",
    ankiIsInNightMode: "isInNightMode",
    ankiIsDisplayingAnswer: "isDisplayingAnswer",
    ankiGetDeckName: "deckName",
    ankiIsActiveNetworkMetered: "isActiveNetworkMetered",
    ankiTtsFieldModifierIsAvailable: "ttsFieldModifierIsAvailable",
    ankiTtsIsSpeaking: "ttsIsSpeaking",
    ankiTtsStop: "ttsStop",
    ankiBuryCard: "buryCard",
    ankiBuryNote: "buryNote",
    ankiSuspendCard: "suspendCard",
    ankiSuspendNote: "suspendNote",
    ankiAddTagToCard: "addTagToCard",
    ankiResetProgress: "resetProgress",
    ankiMarkCard: "markCard",
    ankiToggleFlag: "toggleFlag",
    ankiSearchCard: "searchCard",
    ankiSearchCardWithCallback: "searchCardWithCallback",
    ankiTtsSpeak: "ttsSpeak",
    ankiTtsSetLanguage: "ttsSetLanguage",
    ankiTtsSetPitch: "ttsSetPitch",
    ankiTtsSetSpeechRate: "ttsSetSpeechRate",
    ankiEnableHorizontalScrollbar: "enableHorizontalScrollbar",
    ankiEnableVerticalScrollbar: "enableVerticalScrollbar",
    ankiSetCardDue: "setCardDue",
    ankiShowNavigationDrawer: "showNavigationDrawer",
    ankiShowOptionsMenu: "showOptionsMenu",
    ankiShowToast: "showToast",
    ankiShowAnswer: "showAnswer",
    ankiAnswerEase1: "answerEase1",
    ankiAnswerEase2: "answerEase2",
    ankiAnswerEase3: "answerEase3",
    ankiAnswerEase4: "answerEase4",
    ankiSttSetLanguage: "sttSetLanguage",
    ankiSttStart: "sttStart",
    ankiSttStop: "sttStop",
    ankiAddTagToNote: "addTagToNote",
    ankiSetNoteTags: "setNoteTags",
    ankiGetNoteTags: "getNoteTags",
};

class AnkiDroidJS {
    constructor({ developer, version }) {
        this.developer = developer;
        this.version = version;
    }

    handleRequest = (endpoint, data) => {
        // route all methods to `signal` so the warning is shown
        window.location.href = "signal:jsapi";
    };
}

Object.keys(jsApiList).forEach(method => {
    AnkiDroidJS.prototype[method] = async function (data) {
        const endpoint = jsApiList[method];
        return this.handleRequest(endpoint, data);
    };
});
// endregion
