// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export const colorSwatches = [
    "#f44336",
    "#e91e63",
    "#9c27b0",
    "#673ab7",
    "#3f51b5",
    "#2196f3",
    "#03a9f4",
    "#00bcd4",
    "#009688",
    "#4caf50",
    "#8bc34a",
    "#cddc39",
    "#ffeb3b",
    "#ffc107",
];

export const hslToRgb = (h: number, s: number, l: number): [number, number, number] => {
    let r, g, b;
    if (s === 0) {
        r = g = b = l;
    } else {
        const hue2rgb = function hue2rgb(p, q, t) {
            if (t < 0) t += 1;
            if (t > 1) t -= 1;
            if (t < 1 / 6) return p + (q - p) * 6 * t;
            if (t < 1 / 2) return q;
            if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
            return p;
        };
        const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        const p = 2 * l - q;
        r = hue2rgb(p, q, h + 1 / 3);
        g = hue2rgb(p, q, h);
        b = hue2rgb(p, q, h - 1 / 3);
    }
    return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
};

export const getRgbValue = (hue: number): string => {
    const saturation = 1;
    const lightness = 0.5;
    const [r, g, b] = hslToRgb(hue / 360, saturation, lightness);
    return `rgba(${r}, ${g}, ${b})`;
};

export const getHueValue = (rgbString: string): number => {
    const rgbValues = rgbString.match(/\d+/g)!.map(Number);
    const red = rgbValues[0];
    const green = rgbValues[1];
    const blue = rgbValues[2];

    const r = red / 255;
    const g = green / 255;
    const b = blue / 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    let h;

    if (max == min) {
        h = 0;
    } else {
        const d = max - min;
        switch (max) {
            case r:
                h = (g - b) / d + (g < b ? 6 : 0);
                break;
            case g:
                h = (b - r) / d + 2;
                break;
            case b:
                h = (r - g) / d + 4;
                break;
        }
        h /= 6;
    }

    return Math.round(h * 360);
};

export const rgbaToHexa = (rgbaColor: string): string => {
    const parts = rgbaColor.substring(rgbaColor.indexOf("(")).split(","),
        r = parseInt(parts[0].substring(1), 10),
        g = parseInt(parts[1], 10),
        b = parseInt(parts[2], 10),
        a = parseFloat(parts[3].substring(0, parts[3].length - 1)).toFixed(2);

    const hex = "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
    let alpha = Math.round(parseFloat(a) * 255).toString(16);

    if (alpha.length == 1) {
        alpha = "0" + alpha;
    }

    let color = hex.toUpperCase() + alpha.toUpperCase();
    if (color.length == 9 && color.endsWith("FF")) {
        color = color.slice(0, 7);
    }
    return color;
};

export const hexaToRgba = (hex: string): string => {
    const hasAlpha = (hex.length === 9);
    const r = parseInt(hex.substring(1, 3), 16);
    const g = parseInt(hex.substring(3, 5), 16);
    const b = parseInt(hex.substring(5, 7), 16);
    const a = hasAlpha ? parseInt(hex.substring(7, 9), 16) / 255 : 1;
    return "rgba(" + r + ", " + g + ", " + b + ", " + a.toFixed(2) + ")";
};

export const getCoordsByHexColor = (canvas: HTMLCanvasElement, hexColor: string): { x: number; y: number } => {
    const rgbColor = hexToRgb(hexColor);
    const width = canvas.width;
    const height = canvas.height;
    const imageData = canvas.getContext("2d")!.getImageData(0, 0, width, height).data;
    let coords = { x: 0, y: 0 };

    for (let i = 0; i < imageData.length; i += 4) {
        const r = imageData[i];
        const g = imageData[i + 1];
        const b = imageData[i + 2];

        if (r === rgbColor.r && g === rgbColor.g && b === rgbColor.b) {
            const x = (i / 4) % width;
            const y = Math.floor((i / 4) / width);
            coords = { x: x, y: y };
            return coords;
        }
    }

    return coords;
};

const hexToRgb = (hexColor: string): { r: number; g: number; b: number } => {
    const hex = hexColor.replace("#", "");
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);
    return { r: r, g: g, b: b };
};
