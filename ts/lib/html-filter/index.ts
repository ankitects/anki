// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { filterElementBasic, filterElementExtended, filterElementInternal } from "./element";
import { filterNode } from "./node";

enum FilterMode {
    Basic,
    Extended,
    Internal,
}

const filters: Record<FilterMode, (element: Element) => void> = {
    [FilterMode.Basic]: filterElementBasic,
    [FilterMode.Extended]: filterElementExtended,
    [FilterMode.Internal]: filterElementInternal,
};

const whitespace = /[\n\t ]+/g;

function collapseWhitespace(value: string): string {
    return value.replace(whitespace, " ");
}

function trim(value: string): string {
    return value.trim();
}

const outputHTMLProcessors: Record<FilterMode, (outputHTML: string) => string> = {
    [FilterMode.Basic]: (outputHTML: string): string => trim(collapseWhitespace(outputHTML)),
    [FilterMode.Extended]: trim,
    [FilterMode.Internal]: trim,
};

export function filterHTML(html: string, internal: boolean, extended: boolean): string {
    // https://anki.tenderapp.com/discussions/ankidesktop/39543-anki-is-replacing-the-character-by-when-i-exit-the-html-edit-mode-ctrlshiftx
    if (html.indexOf(">") < 0) {
        return html;
    }

    const template = document.createElement("template");
    template.innerHTML = html;

    const mode = getFilterMode(internal, extended);
    const content = template.content;
    const filter = filterNode(filters[mode]);

    filter(content);

    return outputHTMLProcessors[mode](template.innerHTML);
}

function getFilterMode(internal: boolean, extended: boolean): FilterMode {
    if (internal) {
        return FilterMode.Internal;
    } else if (extended) {
        return FilterMode.Extended;
    } else {
        return FilterMode.Basic;
    }
}
