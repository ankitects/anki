// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import "../base.scss";
import "../../reviewer/reviewer.scss";
import "mathjax/es5/tex-chtml-full.js";
import { renderError } from "../../reviewer";
import { enableNightMode } from "../reviewer/reviewer";
import type { ReviewerRequest } from "../reviewer/reviewerRequest";
import type { InnerReviewerRequest } from "./innerReviewerRequest";

declare const MathJax: any;
const urlParams = new URLSearchParams(location.search);

const style = document.createElement("style");
document.head.appendChild(style);

addEventListener("message", async (e: MessageEvent<InnerReviewerRequest>) => {
    switch (e.data.type) {
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

            break;
        }
        default: {
            console.warn(`Unknown message type: ${e.data.type}`);
            break;
        }
    }
});

const base = document.createElement("base");
base.href = "/";
document.head.appendChild(base);

function postParentMessage(message: ReviewerRequest) {
    window.parent.postMessage(
        message,
        "*",
    );
}

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
