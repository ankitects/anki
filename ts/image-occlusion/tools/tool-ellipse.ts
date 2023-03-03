// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { shapeMaskColor, stopDraw } from "./lib";

export const drawEllipse = (canvas: fabric.Canvas): void => {
    let ellipse, isDown, origX, origY;

    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
        isDown = true;

        const pointer = canvas.getPointer(o.e);
        origX = pointer.x;
        origY = pointer.y;

        ellipse = new fabric.Ellipse({
            left: pointer.x,
            top: pointer.y,
            rx: 1,
            ry: 1,
            fill: shapeMaskColor,
            originX: "center",
            originY: "center",
            transparentCorners: false,
            selectable: true,
        });
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

    canvas.on("mouse:up", function() {
        isDown = false;
        // probably changed from ellipse to rectangle
        if (!ellipse) {
            return;
        }
        if (ellipse.width < 5 || ellipse.height < 5) {
            canvas.remove(ellipse);
        }
        ellipse.setCoords();
    });
};
