// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"use-strict";
import "../base.scss";
import "../../reviewer/reviewer.scss";

import "../../mathjax";
import "mathjax/es5/tex-chtml-full.js";
import { registerPackage } from "@tslib/runtime-require";
import { _runHook, renderError } from "../../reviewer";
import { addBrowserClasses } from "../../reviewer/browser_selector";
import { imageOcclusionAPI } from "../image-occlusion/review";
import { enableNightMode } from "../reviewer/reviewer";
import type { ReviewerRequest } from "../reviewer/reviewerRequest";
import type { InnerReviewerRequest } from "./innerReviewerRequest";

addBrowserClasses();

export const imageOcclusion = imageOcclusionAPI;
export const setupImageCloze = imageOcclusionAPI.setup; // deprecated

function postParentMessage(message: ReviewerRequest) {
    window.parent.postMessage(
        message,
        "*",
    );
}

type Callback = () => void | Promise<void>;

export const onUpdateHook: Array<Callback> = [];
export const onShownHook: Array<Callback> = [];

globalThis.onUpdateHook = onUpdateHook;
globalThis.onShownHook = onShownHook;

declare const MathJax: any;
const urlParams = new URLSearchParams(location.search);
const decoder = new TextDecoder();
const style = document.createElement("style");
document.head.appendChild(style);
const qaDiv = document.createElement("div");
document.body.appendChild(qaDiv);
qaDiv.id = "qa";
qaDiv.style.opacity = "1";

addEventListener("message", async (e: MessageEvent<InnerReviewerRequest>) => {
    switch (e.data.type) {
        case "setstorage": {
            const json = JSON.parse(decoder.decode(e.data.json_buffer));
            Object.assign(storageObj, json);
            break;
        }
        case "html": {
            qaDiv.innerHTML = e.data.value;
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

            onUpdateHook.length = 0;
            onShownHook.length = 0;

            // "".innerHTML =" does not run scripts
            for (const script of qaDiv.querySelectorAll("script")) {
                // strict mode prevents the use of "eval" here
                const parent = script.parentElement!;
                const _script = script.parentElement!.removeChild(script);
                const new_script = document.createElement("script");
                const new_script_text = document.createTextNode(_script.innerHTML);
                new_script.appendChild(new_script_text);
                parent.appendChild(new_script);
            }

            _runHook(onUpdateHook);
            // wait for mathjax to ready
            await MathJax.startup.promise
                .then(() => {
                    // clear MathJax buffers from previous typesets
                    MathJax.typesetClear();

                    return MathJax.typesetPromise([document.body]);
                })
                .catch(renderError("MathJax"));

            _runHook(onShownHook);
            break;
        }
        default: {
            // console.warn(`Unknown message type: ${e.data.type}`);
            break;
        }
    }
});

addEventListener("keydown", (e) => {
    const keyInfo: ReviewerRequest = {
        type: "keypress",
        eventInit: {
            key: e.key,
            code: e.code,
            keyCode: e.keyCode,
            which: e.which,
            altKey: e.altKey,
            ctrlKey: e.ctrlKey,
            shiftKey: e.shiftKey,
            metaKey: e.metaKey,
            repeat: e.repeat,
            bubbles: true,
        },
    };
    if (
        !document.activeElement?.matches("input[type=text], input[type=number], textarea") || e.key === "Enter"
    ) {
        postParentMessage(keyInfo);
    }
});

const base = document.createElement("base");
base.href = "/media/";
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

registerPackage("anki/reviewer", {
    // If you append a function to this each time the question or answer
    // is shown, it will be called before MathJax has been rendered.
    onUpdateHook,
    // If you append a function to this each time the question or answer
    // is shown, it will be called after images have been preloaded and
    // MathJax has been rendered.
    onShownHook,
});
