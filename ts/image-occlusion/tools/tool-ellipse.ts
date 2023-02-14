// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { getQuestionMaskColor, getShapeColor, stopDraw } from "./lib";

export const drawEllipse = (canvas: fabric.Canvas): void => {
    let ellipse, isDown, origX, origY;

    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        isDown = true;

        const pointer = canvas.getPointer(o.e);
        origX = pointer.x;
        origY = pointer.y;

        ellipse = new fabric.Ellipse({
            left: pointer.x,
            top: pointer.y,
            rx: 1,
            ry: 1,
            fill: getShapeColor()!,
            originX: "center",
            originY: "center",
            transparentCorners: false,
            selectable: false,
        });
        ellipse.questionmaskcolor = getQuestionMaskColor();
        canvas.add(ellipse);
    });

    canvas.on("mouse:move", function(o) {
        if (!isDown) return;

        const pointer = canvas.getPointer(o.e);
        ellipse.set({
            rx: Math.abs(origX - pointer.x),
            ry: Math.abs(origY - pointer.y),
        });

        canvas.renderAll();
    });

    canvas.on("mouse:up", function(o) {
        isDown = false;

        const pointer = canvas.getPointer(o.e);
        const rx = Math.abs(origX - pointer.x);
        const ry = Math.abs(origY - pointer.y);
        if (rx < 5 || ry < 5) {
            canvas.remove(ellipse);
        }
        ellipse.setCoords();
    });
};
