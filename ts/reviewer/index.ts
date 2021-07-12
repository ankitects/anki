// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "css-browser-selector/css_browser_selector";
import "jquery/dist/jquery";

import { bridgeCommand } from "lib/bridgecommand";
export { mutateNextCardStates } from "./answering";

declare const MathJax: any;

type Callback = () => void | Promise<void>;

export const onUpdateHook: Array<Callback> = [];
export const onShownHook: Array<Callback> = [];

export const ankiPlatform = "desktop";
let typeans: HTMLInputElement | undefined;

export function getTypedAnswer(): string | null {
    return typeans?.value ?? null;
}

function _runHook(arr: Array<Callback>): Promise<void[]> {
    const promises: (Promise<void> | void)[] = [];

    for (let i = 0; i < arr.length; i++) {
        promises.push(arr[i]());
    }

    return Promise.all(promises);
}

let _updatingQueue: Promise<void> = Promise.resolve();

function _queueAction(action: Callback): void {
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

async function _updateQA(
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
    const renderError =
        (kind: string) =>
        (error: Error): void => {
            const errorMessage = String(error).substring(0, 2000);
            const errorStack = String(error.stack).substring(0, 2000);
            qa.innerHTML =
                `Invalid ${kind} on card: ${errorMessage}\n${errorStack}`.replace(
                    /\n/g,
                    "<br>"
                );
        };

    // hide current card
    qa.style.opacity = "0";

    // update card
    try {
        setInnerHTML(qa, html);
    } catch (error) {
        renderError("HTML")(error);
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

    // and reveal card when processing is done
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
            function () {}
        )
    );
}

const _flagColours = {
    1: "#e25252",
    2: "#ffb347",
    3: "#54c414",
    4: "#578cff",
    5: "#ff82ee",
    6: "#00d1b5",
    7: "#9649dd",
};

export function _drawFlag(flag: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7): void {
    const elem = document.getElementById("_flag")!;
    if (flag === 0) {
        elem.setAttribute("hidden", "");
        return;
    }
    elem.removeAttribute("hidden");
    elem.style.color = _flagColours[flag];
}

export function _drawMark(mark: boolean): void {
    const elem = document.getElementById("_mark")!;
    if (!mark) {
        elem.setAttribute("hidden", "");
    } else {
        elem.removeAttribute("hidden");
    }
}

export function _typeAnsPress(): void {
    const code = (window.event as KeyboardEvent).code;
    if (["Enter", "NumpadEnter"].includes(code)) {
        bridgeCommand("ans");
    }
}

export function _emulateMobile(enabled: boolean): void {
    const list = document.documentElement.classList;
    if (enabled) {
        list.add("mobile");
    } else {
        list.remove("mobile");
    }
}

function allImagesLoaded(): Promise<void[]> {
    return Promise.all(
        Array.from(document.getElementsByTagName("img")).map(imageLoaded)
    );
}

function imageLoaded(img: HTMLImageElement): Promise<void> {
    return img.complete
        ? Promise.resolve()
        : new Promise((resolve) => {
              img.addEventListener("load", () => resolve());
              img.addEventListener("error", () => resolve());
          });
}

function scrollToAnswer(): void {
    document.getElementById("answer")?.scrollIntoView();
}

function injectPreloadLink(href: string, as: string): void {
    const link = document.createElement("link");
    link.rel = "preload";
    link.href = href;
    link.as = as;
    document.head.appendChild(link);
}

function clearPreloadLinks(): void {
    document.head
        .querySelectorAll("link[rel='preload']")
        .forEach((link) => link.remove());
}

function extractImageSrcs(html: string): string[] {
    const fragment = document.createRange().createContextualFragment(html);
    const srcs = [...fragment.querySelectorAll("img[src]")].map(
        (img) => (img as HTMLImageElement).src
    );
    return srcs;
}

function preloadAnswerImages(qHtml: string, aHtml: string): void {
    clearPreloadLinks();
    const aSrcs = extractImageSrcs(aHtml);
    if (aSrcs.length) {
        const qSrcs = extractImageSrcs(qHtml);
        const diff = aSrcs.filter((src) => !qSrcs.includes(src));
        diff.forEach((src) => injectPreloadLink(src, "image"));
    }
}
