// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import "css-browser-selector/css_browser_selector.min";

export { default as $, default as jQuery } from "jquery/dist/jquery";

export { mutateNextCardStates } from "./answering";

import { bridgeCommand } from "lib/bridgecommand";
import { allImagesLoaded, preloadAnswerImages } from "./images";
declare const MathJax: any;

type Callback = () => void | Promise<void>;

export const onUpdateHook: Array<Callback> = [];
export const onShownHook: Array<Callback> = [];

export const ankiPlatform = "desktop";
let typeans: HTMLInputElement | undefined;

export function getTypedAnswer(): string | null {
    return typeans?.value ?? null;
}

function _runHook(
    hooks: Array<Callback>
): Promise<PromiseSettledResult<void | Promise<void>>[]> {
    const promises: (Promise<void> | void)[] = [];

    for (const hook of hooks) {
        try {
            const result = hook();
            promises.push(result);
        } catch (error) {
            console.log("Hook failed: ", error);
        }
    }

    return Promise.allSettled(promises);
}

let _updatingQueue: Promise<void> = Promise.resolve();

export function _queueAction(action: Callback): void {
    _updatingQueue = _updatingQueue.then(action);
}

function setInnerHTML(element: Element, html: string): void {
    for (const oldVideo of element.getElementsByTagName("video")) {
        oldVideo.pause();

        while (oldVideo.firstChild) {
            oldVideo.removeChild(oldVideo.firstChild);
        }

        oldVideo.load();
    }

    element.innerHTML = html;

    for (const oldScript of element.getElementsByTagName("script")) {
        const newScript = document.createElement("script");

        for (const attribute of oldScript.attributes) {
            newScript.setAttribute(attribute.name, attribute.value);
        }

        newScript.appendChild(document.createTextNode(oldScript.innerHTML));
        oldScript.parentNode!.replaceChild(newScript, oldScript);
    }
}

const renderError =
    (type: string) =>
    (error: Error): string => {
        const errorMessage = String(error).substring(0, 2000);
        const errorStack = String(error.stack).substring(0, 2000);
        return `<div>Invalid ${type} on card: ${errorMessage}\n${errorStack}</div>`.replace(
            /\n/g,
            "<br>"
        );
    };

export async function _updateQA(
    html: string,
    _unusused: unknown,
    onupdate: Callback,
    onshown: Callback
): Promise<void> {
    onUpdateHook.length = 0;
    onUpdateHook.push(onupdate);

    onShownHook.length = 0;
    onShownHook.push(onshown);

    const qa = document.getElementById("qa")!;

    qa.style.opacity = "0";

    try {
        setInnerHTML(qa, html);
    } catch (error) {
        setInnerHTML(qa, renderError("html")(error));
    }

    await _runHook(onUpdateHook);

    // wait for mathjax to ready
    await MathJax.startup.promise
        .then(() => {
            // clear MathJax buffers from previous typesets
            MathJax.typesetClear();

            return MathJax.typesetPromise([qa]);
        })
        .catch(renderError("MathJax"));

    qa.style.opacity = "1";

    await _runHook(onShownHook);
}

export function _showQuestion(q: string, a: string, bodyclass: string): void {
    _queueAction(() =>
        _updateQA(
            q,
            null,
            function () {
                // return to top of window
                window.scrollTo(0, 0);

                document.body.className = bodyclass;
            },
            function () {
                // focus typing area if visible
                typeans = document.getElementById("typeans") as HTMLInputElement;
                if (typeans) {
                    typeans.focus();
                }
                // preload images
                allImagesLoaded().then(() => preloadAnswerImages(q, a));
            }
        )
    );
}

function scrollToAnswer(): void {
    document.getElementById("answer")?.scrollIntoView();
}

export function _showAnswer(a: string, bodyclass: string): void {
    _queueAction(() =>
        _updateQA(
            a,
            null,
            function () {
                if (bodyclass) {
                    //  when previewing
                    document.body.className = bodyclass;
                }

                // avoid scrolling to the answer until images load
                allImagesLoaded().then(scrollToAnswer);
            },
            function () {
                /* noop */
            }
        )
    );
}

export function _drawFlag(flag: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7): void {
    const elem = document.getElementById("_flag")!;
    elem.toggleAttribute("hidden", flag === 0);
    elem.style.color = `var(--flag${flag}-fg)`;
}

export function _drawMark(mark: boolean): void {
    document.getElementById("_mark")!.toggleAttribute("hidden", !mark);
}

export function _typeAnsPress(): void {
    const code = (window.event as KeyboardEvent).code;
    if (["Enter", "NumpadEnter"].includes(code)) {
        bridgeCommand("ans");
    }
}

export function _emulateMobile(enabled: boolean): void {
    document.documentElement.classList.toggle("mobile", enabled);
}
