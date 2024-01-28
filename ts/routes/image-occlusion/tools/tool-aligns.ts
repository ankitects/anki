// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { fabric } from "fabric";

export const alignLeft = (canvas: fabric.canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    const activeObject = canvas.getActiveObject();
    if (canvas.getActiveObject().type == "activeSelection") {
        alignLeftGroup(canvas, activeObject);
    } else {
        activeObject.set({ left: 0 });
    }

    activeObject.setCoords();
    canvas.renderAll();
};

export const alignHorizontalCenter = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    const activeObject = canvas.getActiveObject();
    if (canvas.getActiveObject().type == "activeSelection") {
        alignHorizontalCenterGroup(canvas, activeObject);
    } else {
        activeObject.set({ left: canvas.width / 2 - activeObject.width / 2 });
    }

    activeObject.setCoords();
    canvas.renderAll();
};

export const alignRight = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    const activeObject = canvas.getActiveObject();
    if (canvas.getActiveObject().type == "activeSelection") {
        alignRightGroup(canvas, activeObject);
    } else {
        activeObject.set({ left: canvas.getWidth() - activeObject.width });
    }

    activeObject.setCoords();
    canvas.renderAll();
};

export const alignTop = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    const activeObject = canvas.getActiveObject();
    if (canvas.getActiveObject().type == "activeSelection") {
        alignTopGroup(canvas, activeObject);
    } else {
        activeObject.set({ top: 0 });
    }

    activeObject.setCoords();
    canvas.renderAll();
};

export const alignVerticalCenter = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    const activeObject = canvas.getActiveObject();
    if (canvas.getActiveObject().type == "activeSelection") {
        alignVerticalCenterGroup(canvas, activeObject);
    } else {
        activeObject.set({ top: canvas.getHeight() / 2 - activeObject.height / 2 });
    }

    activeObject.setCoords();
    canvas.renderAll();
};

export const alignBottom = (canvas: fabric.Canvas): void => {
    if (!canvas.getActiveObject()) {
        return;
    }

    const activeObject = canvas.getActiveObject();
    if (canvas.getActiveObject().type == "activeSelection") {
        alignBottomGroup(canvas, activeObject);
    } else {
        activeObject.set({ top: canvas.height - activeObject.height });
    }

    activeObject.setCoords();
    canvas.renderAll();
};

// group aligns

const alignLeftGroup = (canvas: fabric.Canvas, group: fabric.Group | fabric.IObject) => {
    const objects = group.getObjects();
    let leftmostShape = objects[0];

    for (let i = 1; i < objects.length; i++) {
        if (objects[i].left < leftmostShape.left) {
            leftmostShape = objects[i];
        }
    }

    objects.forEach((object) => {
        object.left = leftmostShape.left;
        object.setCoords();
    });
};

const alignRightGroup = (canvas: fabric.Canvas, group: fabric.Group | fabric.IObject): void => {
    const objects = group.getObjects();
    let rightmostShape = objects[0];

    for (let i = 1; i < objects.length; i++) {
        if (objects[i].left > rightmostShape.left) {
            rightmostShape = objects[i];
        }
    }

    objects.forEach((object) => {
        object.left = rightmostShape.left + rightmostShape.width - object.width;
        object.setCoords();
    });
};

const alignTopGroup = (canvas: fabric.Canvas, group: fabric.Group | fabric.IObject): void => {
    const objects = group.getObjects();
    let topmostShape = objects[0];

    for (let i = 1; i < objects.length; i++) {
        if (objects[i].top < topmostShape.top) {
            topmostShape = objects[i];
        }
    }

    objects.forEach((object) => {
        object.top = topmostShape.top;
        object.setCoords();
    });
};

const alignBottomGroup = (canvas: fabric.Canvas, group: fabric.Group | fabric.IObject): void => {
    const objects = group.getObjects();
    let bottommostShape = objects[0];

    for (let i = 1; i < objects.length; i++) {
        if (objects[i].top + objects[i].height > bottommostShape.top + bottommostShape.height) {
            bottommostShape = objects[i];
        }
    }

    objects.forEach(function(object) {
        if (object !== bottommostShape) {
            object.set({ top: bottommostShape.top + bottommostShape.height - object.height });
            object.setCoords();
        }
    });
};

const alignHorizontalCenterGroup = (canvas: fabric.Canvas, group: fabric.Group | fabric.IObject) => {
    const objects = group.getObjects();
    let leftmostShape = objects[0];
    let rightmostShape = objects[0];

    for (let i = 1; i < objects.length; i++) {
        if (objects[i].left < leftmostShape.left) {
            leftmostShape = objects[i];
        }
        if (objects[i].left > rightmostShape.left) {
            rightmostShape = objects[i];
        }
    }

    const centerX = (leftmostShape.left + rightmostShape.left + rightmostShape.width) / 2;
    objects.forEach((object) => {
        object.left = centerX - object.width / 2;
        object.setCoords();
    });
};

const alignVerticalCenterGroup = (canvas: fabric.Canvas, group: fabric.Group | fabric.IObject) => {
    const objects = group.getObjects();
    let topmostShape = objects[0];
    let bottommostShape = objects[0];

    for (let i = 1; i < objects.length; i++) {
        if (objects[i].top < topmostShape.top) {
            topmostShape = objects[i];
        }
        if (objects[i].top > bottommostShape.top) {
            bottommostShape = objects[i];
        }
    }

    const centerY = (topmostShape.top + bottommostShape.top + bottommostShape.height) / 2;
    objects.forEach((object) => {
        object.top = centerY - object.height / 2;
        object.setCoords();
    });
};
