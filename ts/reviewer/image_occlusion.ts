// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

window.addEventListener("load", () => {
    window.addEventListener("resize", setupImageCloze);
});

export function setupImageCloze(): void {
    const canvas: HTMLCanvasElement = document.querySelector("canvas")! as HTMLCanvasElement;
    canvas.style.backgroundSize = "100% 100%";
    canvas.style.maxWidth = "100%";
    canvas.style.maxHeight = "100%";

    const ctx: CanvasRenderingContext2D = canvas.getContext("2d")!;
    const image = document.getElementById("img") as HTMLImageElement;
    const size = limitSize({ width: image.width, height: image.height });
    canvas.width = size.width;
    canvas.height = size.height;
    const imageNaturalWidth = image.naturalWidth;
    const canvasWidth = canvas.width;
    let shapeScaler = 1;
    if (imageNaturalWidth > canvasWidth) {
        shapeScaler = canvasWidth / imageNaturalWidth;
    } else {
        shapeScaler = imageNaturalWidth / canvasWidth;
    }
    shapeScaler = Math.round(shapeScaler * 100) / 100;
    drawShapes(ctx, shapeScaler);
}

function drawShapes(ctx: CanvasRenderingContext2D, shapeScaler: number): void {
    const activeCloze = document.querySelectorAll(".cloze");
    const inActiveCloze = document.querySelectorAll(".cloze-inactive");
    const maskColor = getMaskColor();

    for (const clz of activeCloze) {
        const cloze = (<HTMLDivElement> clz);
        const shape = cloze.dataset.shape!;
        const fill = maskColor.questionMask;
        draw(ctx, cloze, shape, fill, shapeScaler);
    }

    for (const clz of inActiveCloze) {
        const cloze = (<HTMLDivElement> clz);
        const shape = cloze.dataset.shape!;
        const fill = maskColor.shapeMask;
        const hideinactive = cloze.dataset.hideinactive == "true";
        if (!hideinactive) {
            draw(ctx, cloze, shape, fill, shapeScaler);
        }
    }
}

function draw(
    ctx: CanvasRenderingContext2D,
    cloze: HTMLDivElement,
    shape: string,
    color: string,
    shapeScaler: number,
): void {
    ctx.fillStyle = color;

    const post_left = parseFloat(cloze.dataset.left!) * shapeScaler;
    const pos_top = parseFloat(cloze.dataset.top!) * shapeScaler;
    const width = parseFloat(cloze.dataset.width!) * shapeScaler;
    const height = parseFloat(cloze.dataset.height!) * shapeScaler;

    switch (shape) {
        case "rect":
            {
                ctx.fillRect(post_left, pos_top, width, height);
            }
            break;

        case "ellipse":
            {
                const rx = parseFloat(cloze.dataset.rx!) * shapeScaler;
                const ry = parseFloat(cloze.dataset.ry!) * shapeScaler;
                ctx.beginPath();
                ctx.ellipse(post_left, pos_top, rx, ry, 0, 0, Math.PI * 2, false);
                ctx.fill();
            }
            break;

        case "polygon":
            {
                const points = JSON.parse(cloze.dataset.points!);
                ctx.beginPath();
                ctx.moveTo(points[0][0] * shapeScaler, points[0][1] * shapeScaler);
                for (let i = 1; i < points.length; i++) {
                    ctx.lineTo(points[i][0] * shapeScaler, points[i][1] * shapeScaler);
                }
                ctx.closePath();
                ctx.fill();
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

function getMaskColor(): { questionMask: string; shapeMask: string } {
    const canvas = document.getElementById("canvas");
    const computedStyle = window.getComputedStyle(canvas!);
    const questionMask = computedStyle.getPropertyValue("--question-mask-color");
    const shapeMask = computedStyle.getPropertyValue("--shape-mask-color");

    return {
        questionMask: questionMask ? questionMask : "#ff8e8e",
        shapeMask: shapeMask ? shapeMask : "#ffeba2",
    };
}
