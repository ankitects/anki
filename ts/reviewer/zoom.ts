// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { bridgeCommand } from "@tslib/bridgecommand";

const ZOOM_STEP = 0.1;
const DEFAULT_SCALE_FACTOR = 1;
const MAXIMUM_SCALE_FACTOR = 5;  // Chromium defaults
const MINIMUM_SCALE_FACTOR = 0.25;

let scaleFactor = DEFAULT_SCALE_FACTOR;
let scaleTimer: null | number = null;

export function triggerScaleStep(sign: number) {
    scaleFactor = Math.min(
        Math.max(scaleFactor * (1 + sign * ZOOM_STEP), MINIMUM_SCALE_FACTOR),
        MAXIMUM_SCALE_FACTOR
    );
    setScaleFactor(scaleFactor);
}

export function setScaleFactor(newScaleFactor: number, interactive = true) {
    const scaledContainer = document.body;
    scaledContainer.style.transform = `scale(${newScaleFactor})`;
    scaleFactor = newScaleFactor;
    if (scaleTimer) {
        clearTimeout(scaleTimer);
    }
    if (interactive) {
        displayScaleInfo(newScaleFactor)
        scaleTimer = setTimeout(() => {
            storeScaleFactor(newScaleFactor);
        }, 100);
    }
}

export function resetScaleFactor() {
    setScaleFactor(DEFAULT_SCALE_FACTOR);
}

function storeScaleFactor(scale: number) {
    bridgeCommand(`scale:${scale}`);
}

const scaleInfoId = "_scaleinfo"

function displayScaleInfo(scaleFactor: number) {
    console.log("displayscaleinfo")
    let scaleInfoBox = document.getElementById(scaleInfoId)
    if (!scaleInfoBox) {
        scaleInfoBox = document.createElement("div")
        document.documentElement.appendChild(scaleInfoBox)
        scaleInfoBox.id = scaleInfoId
    }
    scaleInfoBox.innerHTML = `${Math.round(scaleFactor * 100)}%`
    scaleInfoBox.style.display = "block";
    setTimeout(() => { scaleInfoBox!.style.display = "none"; }, 1000)
}

export function setupWheelZoom() {
    document.addEventListener(
        "wheel",
        (event) => {
            if (!event.ctrlKey) {
                return;
            }
            event.preventDefault();
            triggerScaleStep(-Math.sign(event.deltaY));
        },
        { passive: false }
    );
}
