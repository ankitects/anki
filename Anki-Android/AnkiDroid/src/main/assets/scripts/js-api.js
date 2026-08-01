/*
 * AnkiDroid JavaScript API
 * Version: 0.0.3
 */

/**
 * jsApiList
 *
 * name: method name
 * value: endpoint
 */
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
        this.handleRequest(`init`);
    }

    static init({ developer, version }) {
        return new AnkiDroidJS({ developer, version });
    }

    handleRequest = async (endpoint, data) => {
        const url = `/jsapi/${endpoint}`;
        try {
            const response = await fetch(url, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    developer: this.developer,
                    version: this.version,
                    data,
                }),
            });

            if (!response.ok) {
                throw new Error("Failed to make the request");
            }

            const responseData = await response.text();
            if (endpoint.includes("nextTime") || endpoint.includes("deckName")) {
                return responseData;
            }
            return JSON.parse(responseData);
        } catch (error) {
            console.error("Request error:", error);
            throw error;
        }
    };
}

Object.keys(jsApiList).forEach(method => {
    if (method === "ankiAddTagToNote") {
        AnkiDroidJS.prototype[method] = async function (noteId, tag) {
            console.warn("ankiAddTagToNote is deprecated. Use ankiSetNoteTags instead");
            const endpoint = jsApiList[method];
            const data = JSON.stringify({ noteId, tag });
            return await this.handleRequest(endpoint, data);
        };
        return;
    }
    if (method === "ankiSetNoteTags") {
        AnkiDroidJS.prototype[method] = async function (tags) {
            let hasSpaces = false;
            for (let i = 0; i < tags.length; i++) {
                tags[i] = tags[i].trim();
                if (tags[i].includes(" ") || tags[i].includes("\u3000")) {
                    tags[i] = tags[i].replace(" ", "_").replace("\u3000", "_");
                    hasSpaces = true;
                }
            }
            if (hasSpaces) {
                console.warn("Spaces in tags have been converted to underscores");
            }
            const endpoint = jsApiList[method];
            const data = JSON.stringify({ tags });
            return await this.handleRequest(endpoint, data);
        };
        return;
    }
    if (method === "ankiTtsSpeak") {
        AnkiDroidJS.prototype[method] = async function (text, queueMode = 0) {
            const endpoint = jsApiList[method];
            const data = JSON.stringify({ text, queueMode });
            return await this.handleRequest(endpoint, data);
        };
        return;
    }
    if (method === "ankiShowToast") {
        AnkiDroidJS.prototype[method] = async function (text, shortLength = true) {
            const endpoint = jsApiList[method];
            const data = JSON.stringify({ text, shortLength });
            return await this.handleRequest(endpoint, data);
        };
        return;
    }
    AnkiDroidJS.prototype[method] = async function (data) {
        const endpoint = jsApiList[method];
        return await this.handleRequest(endpoint, data);
    };
});
