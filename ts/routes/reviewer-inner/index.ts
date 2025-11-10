// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import "../base.scss";
import "../../reviewer/reviewer.scss";

import "mathjax/es5/tex-chtml-full.js";
import { renderError } from "../../reviewer";
import { addBrowserClasses } from "../../reviewer/browser_selector";
import { imageOcclusionAPI } from "../image-occlusion/review";
import { enableNightMode } from "../reviewer/reviewer";
import type { ReviewerRequest } from "../reviewer/reviewerRequest";
import type { InnerReviewerRequest } from "./innerReviewerRequest";

const anki = globalThis.anki || {};
anki.imageOcclusion = imageOcclusionAPI;
anki.setupImageCloze = imageOcclusionAPI.setup; // deprecated
addBrowserClasses();

Object.defineProperty(window, "anki", { value: anki });

function postParentMessage(message: ReviewerRequest) {
    window.parent.postMessage(
        message,
        "*",
    );
}

declare const MathJax: any;
const urlParams = new URLSearchParams(location.search);
const decoder = new TextDecoder();
const style = document.createElement("style");
document.head.appendChild(style);

addEventListener("message", async (e: MessageEvent<InnerReviewerRequest>) => {
    switch (e.data.type) {
        case "setstorage": {
            const json = JSON.parse(decoder.decode(e.data.json_buffer));
            Object.assign(storageObj, json);
            break;
        }
        case "html": {
            document.body.innerHTML = e.data.value;
            if (e.data.css) {
                style.innerHTML = e.data.css;
            }
            if (e.data.bodyclass) {
                document.body.className = e.data.bodyclass;
                const theme = urlParams.get("nightMode");
                if (theme !== null) {
                    enableNightMode();
                    document.body.classList.add("night_mode");
                    document.body.classList.add("nightMode");
                }
            }

            // wait for mathjax to ready
            await MathJax.startup.promise
                .then(() => {
                    // clear MathJax buffers from previous typesets
                    MathJax.typesetClear();

                    return MathJax.typesetPromise([document.body]);
                })
                .catch(renderError("MathJax"));

            // "".innerHTML =" does not run scripts
            for (const script of document.querySelectorAll("script")) {
                eval(script.innerHTML);
            }

            break;
        }
        default: {
            // console.warn(`Unknown message type: ${e.data.type}`);
            break;
        }
    }
});

addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
        postParentMessage({ type: "keypress", key: e.key });
    } else if (
        e.key.length == 1 && "1234 ".includes(e.key)
        && !document.activeElement?.matches("input[type=text], input[type=number], textarea")
    ) {
        postParentMessage({ type: "keypress", key: e.key });
    }
});

const base = document.createElement("base");
base.href = "/";
document.head.appendChild(base);

function pycmd(cmd: string) {
    const match = cmd.match(/play:(q|a):(\d+)/);
    if (match) {
        const [_, context, index] = match;
        postParentMessage({
            type: "audio",
            answerSide: context === "a",
            index: parseInt(index),
        });
    }
}
globalThis.pycmd = pycmd;

function _typeAnsPress() {
    const elem = document.getElementById("typeans")! as HTMLInputElement;
    let key = (window.event as KeyboardEvent).key;
    key = key.length == 1 ? key : "";
    postParentMessage(
        { type: "typed", value: elem.value + key },
    );
}
globalThis._typeAnsPress = _typeAnsPress;

const storageObj = {};
const encoder = new TextEncoder();

function updateParentStorage() {
    postParentMessage({ type: "setstorage", json_buffer: encoder.encode(JSON.stringify(storageObj)) });
}

function createStorageProxy() {
    return new Proxy({}, {
        get(_target, prop) {
            switch (prop) {
                case "getItem":
                    return (key) => key in storageObj ? storageObj[key] : null;
                case "setItem":
                    return (key, value) => {
                        storageObj[key] = String(value);
                        updateParentStorage();
                    };
                case "removeItem":
                    return (key) => {
                        delete storageObj[key];
                        updateParentStorage();
                    };
                case "clear":
                    return () => {
                        Object.keys(storageObj).forEach(key => delete storageObj[key]);
                        updateParentStorage();
                    };
                case "key":
                    return (index) => Object.keys(storageObj)[index] ?? null;
                case "length":
                    return Object.keys(storageObj).length;
                default:
                    return storageObj[prop];
            }
        },
        set(_target, prop, value) {
            storageObj[prop] = String(value);
            return true;
        },
        ownKeys() {
            return Object.keys(storageObj);
        },
        getOwnPropertyDescriptor(_target, _prop) {
            return { enumerable: true, configurable: true };
        },
    });
}

const ankiStorage = createStorageProxy();

Object.defineProperty(window, "localStorage", {
    value: ankiStorage,
    writable: false,
    configurable: true,
    enumerable: true,
});
