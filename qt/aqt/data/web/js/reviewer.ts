/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

declare var MathJax: any;

type Callback = () => void | Promise<void>;

var ankiPlatform = "desktop";
var typeans: HTMLElement | undefined;
var _updatingQueue: Promise<void> = Promise.resolve();

var onUpdateHook: Array<Callback>;
var onShownHook: Array<Callback>;

function _runHook(arr: Array<Callback>): Promise<void[]> {
    const promises = [];

    for (let i = 0; i < arr.length; i++) {
        promises.push(arr[i]());
    }

    return Promise.all(promises);
}

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
        oldScript.parentNode.replaceChild(newScript, oldScript);
    }
}

async function _updateQA(
    html: string,
    _unusused: unknown,
    onupdate: Callback,
    onshown: Callback
): Promise<void> {
    onUpdateHook = [onupdate];
    onShownHook = [onshown];

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

    // defer display for up to 100ms to allow images to load
    await Promise.race([allImagesLoaded(), new Promise((r) => setTimeout(r, 100))]);

    // and reveal card when processing is done
    qa.style.opacity = "1";
    await _runHook(onShownHook);
}

function _showQuestion(q: string, bodyclass: string): void {
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
                typeans = document.getElementById("typeans");
                if (typeans) {
                    typeans.focus();
                }
            }
        )
    );
}

function _showAnswer(a: string, bodyclass: string): void {
    _queueAction(() =>
        _updateQA(
            a,
            null,
            function () {
                if (bodyclass) {
                    //  when previewing
                    document.body.className = bodyclass;
                }

                // avoid scrolling to the answer until images load, even if it
                // takes more than 100ms
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

function _drawFlag(flag: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7): void {
    const elem = document.getElementById("_flag");
    if (flag === 0) {
        elem.setAttribute("hidden", "");
        return;
    }
    elem.removeAttribute("hidden");
    elem.style.color = _flagColours[flag];
}

function _drawMark(mark: boolean): void {
    const elem = document.getElementById("_mark");
    if (!mark) {
        elem.setAttribute("hidden", "");
    } else {
        elem.removeAttribute("hidden");
    }
}

function _typeAnsPress(): void {
    if ((window.event as KeyboardEvent).code === "Enter") {
        pycmd("ans");
    }
}

function _emulateMobile(enabled: boolean): void {
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
