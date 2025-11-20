// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { ConfigKey_Bool } from "@generated/anki/config_pb";
import type { ReadClipboardResponse } from "@generated/anki/frontend_pb";
import {
    addMediaFile,
    addMediaFromUrl,
    convertPastedImage,
    extractMediaFiles,
    getAbsoluteMediaPath,
    getConfigBool,
    openFilePicker,
    readClipboard,
    writeClipboard,
} from "@generated/backend";
import * as tr from "@generated/ftl";
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
const URI_LIST_MIME = "text/uri-list";

let isShiftPressed = false;
let lastInternalFieldText = "";

async function wantsExtendedPaste(event: MouseEvent | KeyboardEvent | null = null): Promise<boolean> {
    let stripHtml = (await getConfigBool({
        key: ConfigKey_Bool.PASTE_STRIPS_FORMATTING,
    })).val;
    if ((event && shiftPressed(event)) || isShiftPressed) {
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

async function getUrls(data: DataTransfer | ReadClipboardResponse): Promise<string[]> {
    let dataString: string;
    if (data instanceof DataTransfer) {
        dataString = data.getData(URI_LIST_MIME);
    } else {
        dataString = new TextDecoder().decode(data.data[URI_LIST_MIME]);
    }
    const urls = dataString.split("\n");
    return urls[0] ? urls : [];
}

function getText(data: DataTransfer): string {
    return data.getData("text/plain") ?? "";
}

function getHtml(data: DataTransfer): string {
    return data.getData("text/html") ?? "";
}

async function getImageData(data: DataTransfer | ReadClipboardResponse): Promise<ImageData | null> {
    for (const type of QIMAGE_FORMATS) {
        try {
            const image = data instanceof DataTransfer
                ? data.getData(type)
                : data.data[type];
            if (image) {
                return image;
            } else if (data instanceof DataTransfer) {
                for (const file of data.files ?? []) {
                    if (file.type === type) {
                        return new Uint8Array(await file.arrayBuffer());
                    }
                }
            }
        } catch (e) {
            continue;
        }
    }
    return null;
}

async function retrieveUrl(url: string): Promise<string | null> {
    const response = await addMediaFromUrl({ url });
    if (response.error) {
        alert(response.error);
        return null;
    }
    return response.filename!;
}

async function urlToFile(url: string, allowedSuffixes = mediaSuffixes): Promise<string | null> {
    const lowerUrl = url.toLowerCase();
    for (const suffix of allowedSuffixes) {
        if (lowerUrl.endsWith(`.${suffix}`)) {
            return await retrieveUrl(url);
        }
    }
    // Not a supported type
    return null;
}

export function filenameToLink(filename: string): string {
    const filenameParts = filename.split(".");
    const ext = filenameParts[filenameParts.length - 1].toLowerCase();
    if (imageSuffixes.includes(ext)) {
        return `<img src="${encodeURI(filename)}">`;
    } else {
        return `[sound:${escapeHtml(filename, false)}]`;
    }
}

async function urlToLink(url: string, allowedSuffixes: string[] = mediaSuffixes): Promise<string> {
    const filename = await urlToFile(url, allowedSuffixes);
    if (!filename) {
        const escapedTitle = escapeHtml(decodeURI(url));
        return `<a href="${url}">${escapedTitle}</a>`;
    }
    return filenameToLink(filename);
}

function imageDataToUint8Array(data: ImageData): Uint8Array {
    return typeof data === "string" ? new TextEncoder().encode(data) : data;
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
    return filename;
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
            return filenameToLink(await addPastedImage(data, ext));
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

async function processUrls(
    data: DataTransfer | ReadClipboardResponse,
    _extended: Promise<boolean>,
    allowedSuffixes: string[] = mediaSuffixes,
): Promise<string | null> {
    const urls = await getUrls(data);
    if (urls.length === 0) {
        return null;
    }
    let text = "";
    for (let url of urls) {
        // Chrome likes to give us the URL twice with a \n
        url = url.split("\n")[0].trim();
        text += await urlToLink(url, allowedSuffixes);
    }
    return text;
}

async function getPreferredImageExtension(): Promise<string> {
    if (await getConfigBool({ key: ConfigKey_Bool.PASTE_IMAGES_AS_PNG })) {
        return "png";
    }
    return "jpg";
}

async function processImages(data: DataTransfer, _extended: Promise<boolean>): Promise<string | null> {
    const image = await getImageData(data);
    if (!image) {
        return null;
    }
    const ext = await getPreferredImageExtension();
    return filenameToLink(await addPastedImage(image, ext, true));
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
): Promise<{ html: string | null; internal: boolean }> {
    const data = event instanceof ClipboardEvent ? event.clipboardData! : event.dataTransfer!;
    const html = getHtml(data);
    if (html) {
        return { html, internal: false };
    }
    const urls = await getUrls(data);
    let handlers: ((data: DataTransfer, extended: Promise<boolean>) => Promise<string | null>)[];
    if (urls.length > 0 && urls[0].startsWith("file://")) {
        handlers = [processUrls, processImages, processText];
    } else {
        handlers = [processImages, processUrls, processText];
    }

    for (const handler of handlers) {
        const html = await handler(data, extended);
        if (html) {
            return { html, internal: true };
        }
    }

    return { html: null, internal: false };
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

async function handlePasteOrDrop(event: ClipboardEvent | DragEvent) {
    event.preventDefault();
    let html: string | null = lastInternalFieldText;
    let internal = !!lastInternalFieldText;
    const extended = wantsExtendedPaste(event instanceof ClipboardEvent ? null : event);
    if (!html) {
        // `extended` is passed as a promise because the event's data is apparently cleared if we wait here before calling getData()
        ({ html, internal } = await processDataTransferEvent(event, extended));
    }
    if (html) {
        html = await runPreFilter(html, internal);
        pasteHTML(html, internal, await extended);
    }
}

export async function handlePaste(event: ClipboardEvent) {
    await handlePasteOrDrop(event);
}

export async function handleDrop(event: DragEvent) {
    (event.target as HTMLElement).focus();
    await handlePasteOrDrop(event);
}

export async function handleDragover(event: DragEvent) {
    event.preventDefault();
}

export async function handleKeydown(event: KeyboardEvent) {
    isShiftPressed = shiftPressed(event);
}

export function handleCutOrCopy(event: ClipboardEvent) {
    lastInternalFieldText = getHtml(event.clipboardData!);
}

const FILE_PICKER_MEDIA_KEY = "media";

export async function openFilePickerForSuffixes(suffixes: string[]): Promise<string> {
    const filename = (await openFilePicker({
        title: tr.editingMedia(),
        filterDescription: tr.editingMedia(),
        extensions: suffixes,
        key: FILE_PICKER_MEDIA_KEY,
    })).val;
    return filename;
}

export async function openFilePickerForImageOcclusion(): Promise<string> {
    return await openFilePickerForSuffixes(imageSuffixes);
}

export async function openFilePickerForMedia(): Promise<string> {
    return await openFilePickerForSuffixes(mediaSuffixes);
}

export async function extractImagePathFromHtml(html: string): Promise<string | null> {
    const images = (await extractMediaFiles({ val: html })).vals;
    if (images.length === 0) {
        return null;
    }
    return decodeURI(images[0]);
}
export async function extractImagePathFromData(data: DataTransfer | ReadClipboardResponse): Promise<string | null> {
    const html = await processUrls(data, Promise.resolve(false), imageSuffixes);
    if (html) {
        return await extractImagePathFromHtml(html);
    }
    return null;
}

export async function readImageFromClipboard(): Promise<string | null> {
    const data = await readClipboard({ types: QIMAGE_FORMATS.concat(URI_LIST_MIME) });
    let path = await extractImagePathFromData(data);
    if (!path) {
        const image = await getImageData(data);
        if (!image) {
            return null;
        }
        const ext = await getPreferredImageExtension();
        path = await addPastedImage(image, ext, true);
    }
    return (await getAbsoluteMediaPath({ val: path })).val;
}

export async function writeBlobToClipboard(blob: Blob) {
    await writeClipboard({ data: { [blob.type]: new Uint8Array(await blob.arrayBuffer()) } });
}
