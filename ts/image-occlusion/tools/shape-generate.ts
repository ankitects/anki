// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

export const generateShapeFromCloze = (canvas: fabric.Canvas, clozeStr: string): void => {
    // generate shapes from clozeStr similar to following
    // {{c1::image-occlusion:rect:left=10.0:top=20:width=30:height=10:fill=#ffe34d}}

    const regex = /{{(.*?)}}/g;
    const clozeStrList: string[] = [];
    let match: string[] | null;

    while ((match = regex.exec(clozeStr)) !== null) {
        clozeStrList.push(match[1]);
    }

    const clozeList = {};
    for (const str of clozeStrList) {
        const [prefix, value] = str.split("::image-occlusion:");
        if (!clozeList[prefix]) {
            clozeList[prefix] = [];
        }
        clozeList[prefix].push(value);
    }

    for (const index in clozeList) {
        let shape: fabric.Group | fabric.Rect | fabric.Ellipse | fabric.Polygon;

        if (clozeList[index].length > 1) {
            const group = new fabric.Group();

            clozeList[index].forEach((shape) => {
                const parts = shape.split(":");
                const objectType = parts[0];
                const objectProperties = {
                    angle: "0",
                    left: "0",
                    top: "0",
                    width: "0",
                    height: "0",
                    fill: "#f06",
                    rx: "0",
                    ry: "0",
                    points: "",
                    questionmaskcolor: "",
                };

                for (let i = 1; i < parts.length; i++) {
                    const keyValue = parts[i].split("=");
                    const key = keyValue[0];
                    const value = keyValue[1];
                    objectProperties[key] = value;
                }

                shape = drawShapes(objectType, objectProperties);
                group.addWithUpdate(shape);
            });
            canvas.add(group);
        } else {
            const cloze = clozeList[index][0];
            const parts = cloze.split(":");
            const objectType = parts[0];
            const objectProperties = {
                angle: "0",
                left: "0",
                top: "0",
                width: "0",
                height: "0",
                fill: "#f06",
                rx: "0",
                ry: "0",
                points: "",
            };

            for (let i = 1; i < parts.length; i++) {
                const keyValue = parts[i].split("=");
                const key = keyValue[0];
                const value = keyValue[1];
                objectProperties[key] = value;
            }

            shape = drawShapes(objectType, objectProperties);
            canvas.add(shape);
        }
    }
    canvas.requestRenderAll();
};

const drawShapes = (objectType, objectProperties) => {
    switch (objectType) {
        case "rect": {
            const rect = new fabric.Rect({
                left: parseFloat(objectProperties.left),
                top: parseFloat(objectProperties.top),
                width: parseFloat(objectProperties.width),
                height: parseFloat(objectProperties.height),
                fill: objectProperties.fill,
                selectable: false,
            });
            return rect;
        }

        case "ellipse": {
            const ellipse = new fabric.Ellipse({
                left: parseFloat(objectProperties.left),
                top: parseFloat(objectProperties.top),
                rx: parseFloat(objectProperties.rx),
                ry: parseFloat(objectProperties.ry),
                fill: objectProperties.fill,
                selectable: false,
            });
            return ellipse;
        }

        case "polygon": {
            const points = objectProperties.points.split(" ");
            const polygon = new fabric.Polygon(points, {
                left: parseFloat(objectProperties.left),
                top: parseFloat(objectProperties.top),
                fill: objectProperties.fill,
                selectable: false,
            });
            return polygon;
        }

        default:
            break;
    }
};
