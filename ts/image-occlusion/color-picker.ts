// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "@simonwep/pickr/dist/themes/nano.min.css"; // 'nano' theme

import Pickr from "@simonwep/pickr";
import * as tr from "@tslib/ftl";

import { getQuestionMaskColor, getShapeColor } from "./tools/lib";

export const colorPicker = (elem: string, isFillShape: boolean): Pickr => {
    const quesColorPickerClass = "color-picker-ques";
    const shapeColorPickerClass = "color-picker-shape";

    const pickr = Pickr.create({
        el: elem,
        theme: "nano",
        useAsButton: true,
        autoReposition: true,
        comparison: false,
        default: isFillShape ? getShapeColor() : getQuestionMaskColor(),
        appClass: isFillShape ? shapeColorPickerClass : quesColorPickerClass,
        swatches: [
            "rgba(244, 67, 54, 1)", // Red 500
            "rgba(233, 30, 99, 1)", // Pink 500
            "rgba(156, 39, 176, 1)", // Purple 500
            "rgba(103, 58, 183, 1)", // Deep Purple 500
            "rgba(63, 81, 181, 1)", // Indigo 500
            "rgba(33, 150, 243, 1)", // Blue 500
            "rgba(3, 169, 244, 1)", // Light Blue 500
            "rgba(0, 188, 212, 1)", // Cyan 500
            "rgba(0, 150, 136, 1)", // Teal 500
            "rgba(76, 175, 80, 1)", // Green 500
            "rgba(139, 195, 74, 1)", // Light Green 500
            "rgba(205, 220, 57, 1)", // Lime 500
            "rgba(255, 235, 59, 1)", // Yellow 500
            "rgba(255, 193, 7, 1)", // Amber 500
        ],
        components: {
            preview: true,
            opacity: true,
            hue: true,
            interaction: {
                hex: true,
                rgba: true,
                input: true,
            },
        },
    });

    pickr
        .on("init", () => {
            if (isFillShape) {
                const pickrApp = document.querySelector(`.${shapeColorPickerClass}`)! as HTMLElement;
                setTiltle(pickrApp, tr.notetypesChangeShapeColor());
            } else {
                const pickrApp = document.querySelector(`.${quesColorPickerClass}`)! as HTMLElement;
                setTiltle(pickrApp, tr.notetypesQuestionMaskColor());
            }
        });
    pickr
        .on("change", (color) => {
            const clr = color.toHEXA().toString();
            if (isFillShape) {
                localStorage.setItem("shape-color", clr);
            } else {
                localStorage.setItem("ques-color", clr);
            }
        });

    return pickr;
};

const setTiltle = (pickrApp: HTMLElement, title: string): void => {
    const topPos = parseFloat(pickrApp.style.top) - 50;
    pickrApp.style.top = `${topPos}px`;
    const firstElem = pickrApp.firstChild;
    const titleElem = document.createElement("div");
    titleElem.className = "pickr-title";
    titleElem.innerHTML = title;
    pickrApp.insertBefore(titleElem, firstElem);
};
