// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { FloatingElement } from "@floating-ui/dom";

/**
 * The interface of a function that calls `computePosition` of floating-ui.
 */
export type PositionAlgorithm = (
    reference: HTMLElement,
    floating: FloatingElement
) => Promise<void>;
