// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { playFile } from "@generated/backend";
import { beforeEach, expect, test, vi } from "vitest";

import { filenameToLink, isAudio } from "./data-transfer";

beforeEach(() => {
    vi.clearAllMocks();
});

vi.mock("@generated/backend", async (importOriginal) => ({
    ...(await importOriginal<object>()),
    playFile: vi.fn(),
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
