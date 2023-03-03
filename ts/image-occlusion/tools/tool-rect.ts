// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { shapeMaskColor, stopDraw } from "./lib";

export const drawRectangle = (canvas: fabric.Canvas): void => {
    let rect, isDown, origX, origY;

    stopDraw(canvas);

    canvas.on("mouse:down", function(o) {
        if (o.target) {
            return;
        }
        isDown = true;

        const pointer = canvas.getPointer(o.e);
        origX = pointer.x;
        origY = pointer.y;

        rect = new fabric.Rect({
            left: origX,
            top: origY,
            originX: "left",
            originY: "top",
            width: pointer.x - origX,
            height: pointer.y - origY,
            angle: 0,
            fill: shapeMaskColor,
            transparentCorners: false,
            selectable: true,
        });
        canvas.add(rect);
    });

    canvas.on("mouse:move", function(o) {
        if (!isDown) return;
        const pointer = canvas.getPointer(o.e);

        if (origX > pointer.x) {
            rect.set({
                left: Math.abs(pointer.x),
            });
        }
        if (origY > pointer.y) {
            rect.set({
                top: Math.abs(pointer.y),
            });
        }

        rect.set({
            width: Math.abs(origX - pointer.x),
        });
        rect.set({
            height: Math.abs(origY - pointer.y),
        });

        canvas.renderAll();
    });

    canvas.on("mouse:up", function() {
        isDown = false;
        // probably changed from rectangle to ellipse
        if (!rect) {
            return;
        }
        if (rect.width < 5 || rect.height < 5) {
            canvas.remove(rect);
        }
        rect.setCoords();
    });
};
