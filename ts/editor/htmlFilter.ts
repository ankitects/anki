/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

import {
    filterElementBasic,
    filterElementExtended,
    filterElementInternal,
} from "./htmlFilterElement";
import { filterNode } from "./htmlFilterNode";

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
    [FilterMode.Basic]: (outputHTML: string): string =>
        trim(collapseWhitespace(outputHTML)),
    [FilterMode.Extended]: trim,
    [FilterMode.Internal]: trim,
};

export function filterHTML(html: string, internal: boolean, extended: boolean): string {
    const template = document.createElement("template");
    template.innerHTML = html;

    const mode = internal
        ? FilterMode.Internal
        : extended
        ? FilterMode.Extended
        : FilterMode.Basic;

    const content = template.content;
    const filter = filterNode(filters[mode]);

    filter(content);

    return outputHTMLProcessors[mode](template.innerHTML);
}
