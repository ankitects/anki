// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { borderColor, shapeMaskColor, stopDraw } from "./lib";

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
            left: origX,
            top: origY,
            originX: "left",
            originY: "top",
            rx: pointer.x - origX,
            ry: pointer.y - origY,
            fill: shapeMaskColor,
            transparentCorners: false,
            selectable: true,
            stroke: borderColor,
            strokeWidth: 1,
            strokeUniform: true,
            noScaleCache: false,
        });
        canvas.add(ellipse);
    });

    canvas.on("mouse:move", function(o) {
        if (!isDown) return;

        const pointer = canvas.getPointer(o.e);
        let rx = Math.abs(origX - pointer.x) / 2;
        let ry = Math.abs(origY - pointer.y) / 2;

        if (rx > ellipse.strokeWidth) {
            rx -= ellipse.strokeWidth / 2;
        }
        if (ry > ellipse.strokeWidth) {
            ry -= ellipse.strokeWidth / 2;
        }

        if (pointer.x < origX) {
            ellipse.set({ originX: "right" });
        } else {
            ellipse.set({ originX: "left" });
        }

        if (pointer.y < origY) {
            ellipse.set({ originY: "bottom" });
        } else {
            ellipse.set({ originY: "top" });
        }

        ellipse.set({ rx: rx, ry: ry });

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
            return;
        }

        if (ellipse.originX === "right") {
            ellipse.set({
                originX: "left",
                left: ellipse.left - ellipse.width + ellipse.strokeWidth,
            });
        }

        if (ellipse.originY === "bottom") {
            ellipse.set({
                originY: "top",
                top: ellipse.top - ellipse.height + ellipse.strokeWidth,
            });
        }

        ellipse.setCoords();
    });

    canvas.on("object:modified", function(o) {
        const activeObject = o.target;
        if (!activeObject) {
            return;
        }

        const newRx = activeObject.rx * activeObject.scaleX;
        const newRy = activeObject.ry * activeObject.scaleY;
        const newWidth = activeObject.width * activeObject.scaleX;
        const newHeight = activeObject.height * activeObject.scaleY;

        activeObject.set({
            rx: newRx,
            ry: newRy,
            width: newWidth,
            height: newHeight,
            scaleX: 1,
            scaleY: 1,
        });
    });
};
