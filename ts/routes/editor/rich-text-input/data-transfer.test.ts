// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { ConfigKey_Bool } from "@generated/anki/config_pb";
import { ConvertPastedImageResponse, ReadClipboardResponse } from "@generated/anki/frontend_pb";
import { Bool, String as GenericString } from "@generated/anki/generic_pb";
import {
    addMediaFile,
    convertPastedImage,
    getAbsoluteMediaPath,
    getConfigBool,
    playFile,
    readClipboard,
} from "@generated/backend";
import { Buffer } from "node:buffer";
import { webcrypto } from "node:crypto";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import { pasteHTML } from "../old-editor-adapter";
import { filenameToLink, handlePaste, isAudio, readImageFromClipboard } from "./data-transfer";

// jsdom does not provide these clipboard APIs.
class TestDataTransfer {
    files: File[] = [];

    constructor(private data: Record<string, string>) {}

    getData(type: string): string {
        return this.data[type] ?? "";
    }
}

class TestClipboardEvent extends Event {
    readonly clipboardData: DataTransfer | null;

    constructor(type: string, init: ClipboardEventInit) {
        super(type, init);
        this.clipboardData = init.clipboardData ?? null;
    }
}

const pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII=";
const pngBytes = new Uint8Array(Buffer.from(pngBase64, "base64"));

beforeEach(() => {
    vi.resetAllMocks();
    vi.stubGlobal("DataTransfer", TestDataTransfer);
    vi.stubGlobal("ClipboardEvent", TestClipboardEvent);
    vi.stubGlobal("crypto", webcrypto);
    vi.mocked(getConfigBool).mockResolvedValue(new Bool({ val: false }));
    vi.mocked(addMediaFile).mockImplementation(async ({ desiredName }) => new GenericString({ val: desiredName }));
});

afterEach(() => {
    vi.unstubAllGlobals();
});

vi.mock("@generated/backend", async (importOriginal) => ({
    ...(await importOriginal<object>()),
    addMediaFile: vi.fn(),
    convertPastedImage: vi.fn(),
    getAbsoluteMediaPath: vi.fn(),
    getConfigBool: vi.fn(),
    playFile: vi.fn(),
    readClipboard: vi.fn(),
}));

vi.mock("../old-editor-adapter", () => ({
    pasteHTML: vi.fn(),
}));

test("isAudio recognizes audio/video suffixes regardless of case", () => {
    expect(isAudio("clip.mp3")).toBe(true);
    expect(isAudio("clip.MP3")).toBe(true);
    expect(isAudio("clip.wav")).toBe(true);
    expect(isAudio("clip.mp4")).toBe(true);
});

test("isAudio returns false for images and files without a matching suffix", () => {
    expect(isAudio("photo.png")).toBe(false);
    expect(isAudio("photo.jpg")).toBe(false);
    expect(isAudio("notes.txt")).toBe(false);
});

test("filenameToLink and isAudio agree on what counts as audio", () => {
    const link = filenameToLink("clip.mp3");
    expect(link).toBe("[sound:clip.mp3]");
    expect(isAudio("clip.mp3")).toBe(true);
    expect(vi.mocked(playFile)).toHaveBeenCalledWith({ val: "clip.mp3" });
});

test("filenameToLink returns bare filename if unrecognized", () => {
    const link = filenameToLink("test.foo");
    expect(link).toBe("test.foo");
    expect(vi.mocked(playFile)).toHaveBeenCalledTimes(0);
});

test.each(["text/plain", "text/html"])(
    "pasting a base64 PNG from %s preserves image bytes and inserts a local image",
    async (type) => {
        const url = `data:image/png;base64,${pngBase64}`;
        const data = new TestDataTransfer({ [type]: type === "text/html" ? `<img src="${url}">` : url });
        const event = new ClipboardEvent("paste", {
            clipboardData: data as unknown as DataTransfer,
            cancelable: true,
        });

        await handlePaste(event, false);

        expect(addMediaFile).toHaveBeenCalledExactlyOnceWith({
            desiredName: expect.stringMatching(/^paste-[0-9a-f]{40}\.png$/),
            data: pngBytes,
        });
        const filename = vi.mocked(addMediaFile).mock.calls[0][0].desiredName;
        const template = document.createElement("template");
        template.innerHTML = vi.mocked(pasteHTML).mock.calls[0][0];
        expect(template.content.querySelectorAll("img")).toHaveLength(1);
        expect(template.content.querySelector("img")?.getAttribute("src")).toBe(filename);
    },
);

test.each([
    { pasteAsPng: true, extension: "png" },
    { pasteAsPng: false, extension: "jpg" },
])("clipboard images use $extension when the PNG preference is $pasteAsPng", async ({ pasteAsPng, extension }) => {
    vi.mocked(getConfigBool).mockResolvedValue(new Bool({ val: pasteAsPng }));
    vi.mocked(readClipboard).mockResolvedValue(new ReadClipboardResponse({ data: { "image/png": pngBytes } }));
    vi.mocked(convertPastedImage).mockResolvedValue(new ConvertPastedImageResponse({ data: pngBytes }));
    vi.mocked(getAbsoluteMediaPath).mockImplementation(async ({ val }) => new GenericString({ val: `/media/${val}` }));

    const path = await readImageFromClipboard();

    expect(getConfigBool).toHaveBeenCalledWith({ key: ConfigKey_Bool.PASTE_IMAGES_AS_PNG });
    expect(convertPastedImage).toHaveBeenCalledExactlyOnceWith({ data: pngBytes, ext: extension });
    expect(path).toMatch(new RegExp(`^/media/paste-[0-9a-f]{40}\\.${extension}$`));
});
