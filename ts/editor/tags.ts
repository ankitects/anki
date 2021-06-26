// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function normalizeTagname(tagname: string): string {
    let trimmed = tagname.trim();

    while (true) {
        if (trimmed.startsWith("::")) {
            trimmed = trimmed.slice(2).trimStart();
        } else if (trimmed.endsWith("::")) {
            trimmed = trimmed.slice(0, -2).trimEnd();
        } else {
            return trimmed;
        }
    }
}

interface Tag {
    id: string;
    name: string;
    input: HTMLInputElement;
    active: boolean;
    blink: boolean;
}

export function attachId(name: string, active = false): Tag {
    return {
        id: Math.random().toString(36).substring(2),
        name,
        input: null as unknown as HTMLInputElement,
        active,
        blink: false,
    };
}

export function getName(tag: Tag): string {
    return tag.name;
}
