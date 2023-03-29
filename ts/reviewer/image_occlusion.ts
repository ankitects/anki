// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

window.addEventListener("load", () => {
    window.addEventListener("resize", setupImageCloze);
});

export function setupImageCloze(): void {
    const canvas: HTMLCanvasElement = document.querySelector("canvas")! as HTMLCanvasElement;
    canvas.style.maxWidth = "100%";
    canvas.style.maxHeight = "95vh";

    const ctx: CanvasRenderingContext2D = canvas.getContext("2d")!;
    const image = document.getElementById("img") as HTMLImageElement;
    const size = limitSize({ width: image.naturalWidth, height: image.naturalHeight });
    canvas.width = size.width;
    canvas.height = size.height;

    // set height for div container (used 'relative' in css)
    const container = document.getElementById("container") as HTMLDivElement;
    container.style.height = `${image.height}px`;

    // setup button for toggle image occlusion
    const button = document.getElementById("toggle");
    if (button) {
        button.addEventListener("click", toggleMasks);
    }

    drawShapes(ctx);
}

function drawShapes(ctx: CanvasRenderingContext2D): void {
    const activeCloze = document.querySelectorAll(".cloze");
    const inActiveCloze = document.querySelectorAll(".cloze-inactive");
    const shapeProperty = getShapeProperty();

    for (const clz of activeCloze) {
        const cloze = (<HTMLDivElement> clz);
        const shape = cloze.dataset.shape!;
        const fill = shapeProperty.activeShapeColor;
        draw(ctx, cloze, shape, fill, shapeProperty.activeBorder);
    }

    for (const clz of inActiveCloze) {
        const cloze = (<HTMLDivElement> clz);
        const shape = cloze.dataset.shape!;
        const fill = shapeProperty.inActiveShapeColor;
        const hideinactive = cloze.dataset.hideinactive == "true";
        if (!hideinactive) {
            draw(ctx, cloze, shape, fill, shapeProperty.inActiveBorder);
        }
    }
}

function draw(
    ctx: CanvasRenderingContext2D,
    cloze: HTMLDivElement,
    shape: string,
    color: string,
    border: { width: number; color: string },
): void {
    ctx.fillStyle = color;

    const posLeft = parseFloat(cloze.dataset.left!);
    const posTop = parseFloat(cloze.dataset.top!);
    const width = parseFloat(cloze.dataset.width!);
    const height = parseFloat(cloze.dataset.height!);

    switch (shape) {
        case "rect":
            {
                ctx.strokeStyle = border.color;
                ctx.lineWidth = border.width;
                ctx.fillRect(posLeft, posTop, width, height);
                ctx.strokeRect(posLeft, posTop, width, height);
            }
            break;

        case "ellipse":
            {
                const rx = parseFloat(cloze.dataset.rx!);
                const ry = parseFloat(cloze.dataset.ry!);
                const newLeft = posLeft + rx;
                const newTop = posTop + ry;
                ctx.beginPath();
                ctx.strokeStyle = border.color;
                ctx.lineWidth = border.width;
                ctx.ellipse(newLeft, newTop, rx, ry, 0, 0, Math.PI * 2, false);
                ctx.closePath();
                ctx.fill();
                ctx.stroke();
            }
            break;

        case "polygon":
            {
                const points = JSON.parse(cloze.dataset.points!);
                ctx.beginPath();
                ctx.strokeStyle = border.color;
                ctx.lineWidth = border.width;
                ctx.moveTo(points[0][0], points[0][1]);
                for (let i = 1; i < points.length; i++) {
                    ctx.lineTo(points[i][0], points[i][1]);
                }
                ctx.closePath();
                ctx.fill();
                ctx.stroke();
            }
            break;

        default:
            break;
    }
}

// following function copy+pasted from mask-editor.ts,
// so update both, if it changes
function limitSize(size: { width: number; height: number }): { width: number; height: number; scalar: number } {
    const maximumPixels = 1000000;
    const { width, height } = size;

    const requiredPixels = width * height;
    if (requiredPixels <= maximumPixels) return { width, height, scalar: 1 };

    const scalar = Math.sqrt(maximumPixels) / Math.sqrt(requiredPixels);
    return {
        width: Math.floor(width * scalar),
        height: Math.floor(height * scalar),
        scalar: scalar,
    };
}

function getShapeProperty(): {
    activeShapeColor: string;
    inActiveShapeColor: string;
    activeBorder: { width: number; color: string };
    inActiveBorder: { width: number; color: string };
} {
    const canvas = document.getElementById("canvas");
    const computedStyle = window.getComputedStyle(canvas!);
    // it may throw error if the css variable is not defined
    try {
        // shape color
        const activeShapeColor = computedStyle.getPropertyValue("--active-shape-color");
        const inActiveShapeColor = computedStyle.getPropertyValue("--inactive-shape-color");
        // inactive shape border
        const inActiveShapeBorder = computedStyle.getPropertyValue("--inactive-shape-border");
        const inActiveBorder = inActiveShapeBorder.split(" ").filter((x) => x);
        const inActiveShapeBorderWidth = parseFloat(inActiveBorder[0]);
        const inActiveShapeBorderColor = inActiveBorder[1];
        // active shape border
        const activeShapeBorder = computedStyle.getPropertyValue("--active-shape-border");
        const activeBorder = activeShapeBorder.split(" ").filter((x) => x);
        const activeShapeBorderWidth = parseFloat(activeBorder[0]);
        const activeShapeBorderColor = activeBorder[1];

        return {
            activeShapeColor: activeShapeColor ? activeShapeColor : "#ff8e8e",
            inActiveShapeColor: inActiveShapeColor ? inActiveShapeColor : "#ffeba2",
            activeBorder: {
                width: activeShapeBorderWidth ? activeShapeBorderWidth : 1,
                color: activeShapeBorderColor ? activeShapeBorderColor : "#212121",
            },
            inActiveBorder: {
                width: inActiveShapeBorderWidth ? inActiveShapeBorderWidth : 1,
                color: inActiveShapeBorderColor ? inActiveShapeBorderColor : "#212121",
            },
        };
    } catch {
        // return default values
        return {
            activeShapeColor: "#ff8e8e",
            inActiveShapeColor: "#ffeba2",
            activeBorder: {
                width: 1,
                color: "#212121",
            },
            inActiveBorder: {
                width: 1,
                color: "#212121",
            },
        };
    }
}

const toggleMasks = (): void => {
    const canvas = document.getElementById("canvas") as HTMLCanvasElement;
    const display = canvas.style.display;
    if (display === "none") {
        canvas.style.display = "unset";
    } else {
        canvas.style.display = "none";
    }
};
