// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { decoratedElements } from "../decorated-elements";

export function storedToUndecorated(html: string): string {
    return decoratedElements.toUndecorated(html);
}

export function undecoratedToStored(html: string): string {
    return decoratedElements.toStored(html);
}
