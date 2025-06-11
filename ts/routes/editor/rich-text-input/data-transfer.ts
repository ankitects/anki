// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { ConfigKey_Bool } from "@generated/anki/config_pb";
import { addMediaFile, convertPastedImage, getConfigBool, retrieveUrl as retrieveUrlBackend } from "@generated/backend";
import { bridgeCommand } from "@tslib/bridgecommand";
import { shiftPressed } from "@tslib/keys";
import { pasteHTML } from "../old-editor-adapter";

type ImageData = string | Uint8Array;
const imageSuffixes = ["jpg", "jpeg", "png", "gif", "svg", "webp", "ico", "avif"];
const audioSuffixes = [
    "3gp",
    "aac",
    "avi",
    "flac",
    "flv",
    "m4a",
    "mkv",
    "mov",
    "mp3",
    "mp4",
    "mpeg",
    "mpg",
    "oga",
    "ogg",
    "ogv",
    "ogx",
    "opus",
    "spx",
    "swf",
    "wav",
    "webm",
];
const mediaSuffixes = [...imageSuffixes, ...audioSuffixes];

function imageDataToUint8Array(data: ImageData): Uint8Array {
    return typeof data === "string" ? new TextEncoder().encode(data) : data;
}

async function wantsExtendedPaste(event: MouseEvent | KeyboardEvent): Promise<boolean> {
    let stripHtml = (await getConfigBool({
        key: ConfigKey_Bool.PASTE_STRIPS_FORMATTING,
    })).val;
    if (shiftPressed(event)) {
        stripHtml = !stripHtml;
    }
    return !stripHtml;
}

function escapeHtml(text: string, quote = true): string {
    text = text
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
    if (quote) {
        text = text.replaceAll("\"", "&quot;")
            .replaceAll("'", "&#039;");
    }
    return text;
}
function getUrls(data: DataTransfer): string[] {
    const urls = data.getData("text/uri-list").split("\n");
    return urls[0] ? urls : [];
}

function getText(data: DataTransfer): string {
    return data.getData("text/plain") ?? "";
}

const QIMAGE_FORMATS = [
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/svg+xml",
    "image/bmp",
    "image/x-portable-bitmap",
    "image/x-portable-graymap",
    "image/x-portable-pixmap",
    "image/x-xbitmap",
    "image/x-xpixmap",
];

async function getImageData(data: DataTransfer): Promise<ImageData | null> {
    for (const type of QIMAGE_FORMATS) {
        const image = data.getData(type);
        if (image) {
            return image;
        } else {
            for (const file of data.files ?? []) {
                if (file.type === type) {
                    return new Uint8Array(await file.arrayBuffer());
                }
            }
        }
    }
    return null;
}

async function retrieveUrl(url: string): Promise<string | null> {
    const response = await retrieveUrlBackend({ val: url });
    if (response.error) {
        alert(response.error);
        return null;
    }

    return response.filename;
}

async function urlToFile(url: string): Promise<string | null> {
    const lowerUrl = url.toLowerCase();
    for (const suffix of mediaSuffixes) {
        if (lowerUrl.endsWith(`.${suffix}`)) {
            return await retrieveUrl(url);
        }
    }
    // Not a supported type
    return null;
}

function filenameToLink(filename: string): string {
    const filenameParts = filename.split(".");
    const ext = filenameParts[filenameParts.length - 1].toLowerCase();
    if (imageSuffixes.includes(ext)) {
        return `<img src="${encodeURI(filename)}">`;
    } else {
        return `[sound:${escapeHtml(filename, false)}]`;
    }
}

async function urlToLink(url: string): Promise<string> {
    const filename = await urlToFile(url);
    if (!filename) {
        const escapedTitle = escapeHtml(decodeURI(url));
        return `<a href="${url}">${escapedTitle}</a>`;
    }
    return filenameToLink(filename);
}

async function checksum(data: string | Uint8Array): Promise<string> {
    const bytes = imageDataToUint8Array(data);
    const hashBuffer = await crypto.subtle.digest("SHA-1", bytes);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, "0")).join("");

    return hashHex;
}

async function addMediaFromData(filename: string, data: ImageData): Promise<string> {
    filename = (await addMediaFile({
        desiredName: filename,
        data: imageDataToUint8Array(data),
    })).val;
    return filenameToLink(filename);
}

async function pastedImageFilename(data: ImageData, ext: string): Promise<string> {
    const csum = await checksum(data);
    return `paste-${csum}.${ext}`;
}

async function addPastedImage(data: ImageData, ext: string, convert = false): Promise<string> {
    const filename = await pastedImageFilename(data, ext);
    if (convert) {
        data = (await convertPastedImage({ data: imageDataToUint8Array(data), ext })).data;
    }
    return await addMediaFromData(filename, data);
}

async function inlinedImageToFilename(src: string): Promise<string> {
    const prefix = "data:image/";
    const suffix = ";base64,";
    for (let ext of ["jpg", "jpeg", "png", "gif"]) {
        const fullPrefix = prefix + ext + suffix;
        if (src.startsWith(fullPrefix)) {
            const b64data = src.slice(fullPrefix.length).trim();
            const data = atob(b64data);
            if (ext === "jpeg") {
                ext = "jpg";
            }
            return await addPastedImage(data, ext);
        }
    }
    return "";
}

async function inlinedImageToLink(src: string): Promise<string> {
    const filename = await inlinedImageToFilename(src);
    if (filename) {
        return filenameToLink(filename);
    }

    return "";
}

function isURL(s: string): boolean {
    s = s.toLowerCase();
    const prefixes = ["http://", "https://", "ftp://", "file://"];
    return prefixes.some(prefix => s.startsWith(prefix));
}

async function processUrls(data: DataTransfer, _extended: Promise<boolean>): Promise<string | null> {
    const urls = getUrls(data);
    if (urls.length === 0) {
        return null;
    }
    let text = "";
    for (let url of urls) {
        // Chrome likes to give us the URL twice with a \n
        const lines = url.split("\n");
        url = lines[0];
        text += await urlToLink(url);
    }

    return text;
}

async function processImages(data: DataTransfer, _extended: Promise<boolean>): Promise<string | null> {
    const image = await getImageData(data);
    if (!image) {
        return null;
    }
    let ext: string;
    if (await getConfigBool({ key: ConfigKey_Bool.PASTE_IMAGES_AS_PNG })) {
        ext = "png";
    } else {
        ext = "jpg";
    }
    return await addPastedImage(image, ext, true);
}

async function processText(data: DataTransfer, extended: Promise<boolean>): Promise<string | null> {
    function replaceSpaces(match: string, p1: string): string {
        return `${p1.replaceAll(" ", "&nbsp;")} `;
    }

    const text = getText(data);
    if (text.length === 0) {
        return null;
    }
    const processed: string[] = [];
    for (const line of text.split("\n")) {
        for (let token of line.split(/(\S+)/g)) {
            // Inlined data in base64?
            if ((await extended) && token.startsWith("data:image/")) {
                processed.push(await inlinedImageToLink(token));
            } else if ((await extended) && isURL(token)) {
                // If the user is pasting an image or sound link, convert it to local, otherwise paste as a hyperlink
                processed.push(await urlToLink(token));
            } else {
                token = escapeHtml(token).replaceAll("\t", " ".repeat(4));

                // If there's more than one consecutive space,
                // use non-breaking spaces for the second one on
                token = token.replace(/ ( +)/g, replaceSpaces);
                processed.push(token);
            }
        }
        processed.push("<br>");
    }
    processed.pop();
    return processed.join("");
}

async function processDataTransferEvent(
    event: ClipboardEvent | DragEvent,
    extended: Promise<boolean>,
): Promise<string | null> {
    const data = event instanceof ClipboardEvent ? event.clipboardData! : event.dataTransfer!;
    const html = data.getData("text/html");
    if (html) {
        return html;
    }
    const urls = getUrls(data);
    let handlers: ((data: DataTransfer, extended: Promise<boolean>) => Promise<string | null>)[];
    if (urls.length > 0 && urls[0].startsWith("file://")) {
        handlers = [processUrls, processImages, processText];
    } else {
        handlers = [processImages, processUrls, processText];
    }

    for (const handler of handlers) {
        const html = await handler(data, extended);
        if (html) {
            return html;
        }
    }

    return null;
}

async function runPreFilter(html: string, internal = false): Promise<string> {
    const template = document.createElement("template");
    template.innerHTML = html;
    const content = template.content;
    for (const img of content.querySelectorAll("img")) {
        let src = img.getAttribute("src");
        if (!src) {
            continue;
        }
        // In internal pastes, rewrite mediasrv references to relative
        if (internal) {
            const match = /http:\/\/127\.0\.0\.1:\d+\/(.*)$/.exec(src);
            if (match) {
                src = match[1];
            }
        } else {
            if (isURL(src)) {
                const filename = await retrieveUrl(src);
                if (filename) {
                    src = filename;
                }
            } else if (src.startsWith("data:image/")) {
                src = await inlinedImageToFilename(src);
            }
        }
        img.src = src;
    }
    return template.innerHTML;
}

export async function handlePaste(event: ClipboardEvent) {
    // bridgeCommand("paste");
    event.preventDefault();
    let html = await processDataTransferEvent(event, Promise.resolve(true));
    if (html) {
        html = await runPreFilter(html);
        pasteHTML(html, false, false);
    }
}

export async function handleDrop(event: DragEvent) {
    event.preventDefault();
    // `extended` is passed as a promise because the event's data is apparently cleared if we wait here before calling getData()
    let html = await processDataTransferEvent(event, wantsExtendedPaste(event));
    if (html) {
        html = await runPreFilter(html);
        pasteHTML(html, false, false);
    }
}

export async function handleDragover(event: DragEvent) {
    event.preventDefault();
}

export function handleCutOrCopy() {
    bridgeCommand("cutOrCopy");
}
