// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { bridgeCommand } from "@tslib/bridgecommand";

// Chromium defaults
const PRESET_ZOOM_FACTORS = [
    0.25,
    1 / 3.0,
    0.5,
    2 / 3.0,
    0.75,
    0.8,
    0.9,
    1.0,
    1.1,
    1.25,
    1.5,
    1.75,
    2.0,
    2.5,
    3.0,
    4.0,
    5.0,
];
const DEFAULT_ZOOM_STEP = 7;

let zoomStep = DEFAULT_ZOOM_STEP;
let zoomSaveTimer: number | null = null;

export function triggerZoomStep(sign: number): void {
    const step = zoomStep + sign
    if (step < 0 || step > (PRESET_ZOOM_FACTORS.length - 1)) {
        return
    }

    setZoomStep(step);
}

export function setZoomStep(step: number, interactive = true): void {
    const zoomedContainer = document.body;
    const zoomFactor = PRESET_ZOOM_FACTORS[step]
    if (zoomFactor === undefined) {
        return
    }
    zoomedContainer.style.transform = `scale(${zoomFactor})`;
    zoomStep = step
    if (zoomSaveTimer) {
        clearTimeout(zoomSaveTimer);
    }
    if (interactive) {
        displayZoomInfo(zoomFactor);
        zoomSaveTimer = setTimeout(() => {
            storeZoomStep(step);
        }, 100);
    }
}

export function resetZoom(): void {
    setZoomStep(DEFAULT_ZOOM_STEP)
}

function storeZoomStep(step: number) {
    bridgeCommand(`zoom:${step}`);
}

const zoomInfoId = "_zoominfo";
let zoomInfoTimer: number | null = null;

function displayZoomInfo(zoomFactor: number) {
    let zoomInfoBox = document.getElementById(zoomInfoId);
    if (!zoomInfoBox) {
        zoomInfoBox = document.createElement("div");
        document.documentElement.appendChild(zoomInfoBox);
        zoomInfoBox.id = zoomInfoId;
    }
    if (zoomInfoTimer) {
        clearTimeout(zoomInfoTimer)
    }
    zoomInfoBox.innerHTML = `${Math.round(zoomFactor * 100)}%`;
    zoomInfoBox.style.display = "block";
    zoomInfoTimer = setTimeout(() => {
        zoomInfoBox!.style.display = "none";
    }, 1000);
}

export function setupWheelZoom(): void {
    document.addEventListener(
        "wheel",
        (event) => {
            if (!(event.ctrlKey && event.shiftKey)) {
                return;
            }
            event.preventDefault();
            triggerZoomStep(-Math.sign(event.deltaY));
        },
        { passive: false }
    );
}
