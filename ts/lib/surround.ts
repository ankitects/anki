// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { registerPackage } from "./register-package";

function escapeHTML(text: string): string {
    return text.replace("&", "&amp").replace("<", "&lt").replace(">", "&gt");
}

type Item = Node | string | null;
export function surround(
    root: Document | ShadowRoot,
    ...circumfixes: (Element | [Item, Item])[]
): void {}

registerPackage("anki/surround", {});
