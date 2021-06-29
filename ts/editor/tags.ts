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

export interface Tag {
    id: string;
    name: string;
    selected: boolean;
    flash: () => void;
}

const noop = () => {};

export function attachId(name: string): Tag {
    return {
        id: Math.random().toString(36).substring(2),
        name,
        selected: false,
        flash: noop,
    };
}

export function getName(tag: Tag): string {
    return tag.name;
}
