// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SurroundFormat } from "./surround-format";

export interface MatchType<T = never> {
    /**
     * The element represented by the match will be removed from the document.
     */
    remove(): void;
    /**
     * If the element has some styling applied that matches the format, but
     * might contain some styling above that, you should use clear and do the
     * modifying in the callback.
     *
     * @remarks
     * You can still call `match.remove()` in the callback
     *
     * @example
     * If you want to match bold elements, `<span class="myclass" style="font-weight:bold"/>
     * should match via `clear`, but should not be removed, because it still
     * has a class applied, even if the `style` attribute is removed.
     */
    clear(callback: () => void): void;
    /**
     * Used to sustain a value that is needed to recreate the surrounding.
     * Can be retrieved from the FormattingNode interface via `.getCache`.
     */
    setCache(value: T): void;
}

type Callback = () => void;

export class Match<T> implements MatchType<T> {
    private _shouldRemove = false;
    remove(): void {
        this._shouldRemove = true;
    }

    private _callback: Callback | null = null;
    clear(callback: Callback): void {
        this._callback = callback;
    }

    get matches(): boolean {
        return Boolean(this._callback) || this._shouldRemove;
    }

    /**
     * @internal
     */
    shouldRemove(): boolean {
        this._callback?.();
        this._callback = null;
        return this._shouldRemove;
    }

    cache: T | null = null;
    setCache(value: T): void {
        this.cache = value;
    }
}

class FakeMatch implements MatchType<never> {
    public value = false;

    remove(): void {
        this.value = true;
    }

    clear(): void {
        this.value = true;
    }

    setCache(): void {
        // noop
    }
}

/**
 * Turns the format.matcher into a function that can be used with `findAbove`.
 */
export function boolMatcher<T>(
    format: SurroundFormat<T>,
): (element: Element) => boolean {
    return function(element: Element): boolean {
        const fake = new FakeMatch();
        format.matcher(element as HTMLElement | SVGElement, fake);
        return fake.value;
    };
}
