// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import DOMPurify from "dompurify";

export function sanitize(html: string): string {
    // We need to treat the text as a document fragment, or a style tag
    // at the start of input will be discarded.
    return DOMPurify.sanitize(html, { FORCE_BODY: true });
}
