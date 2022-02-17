// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface MatchType {
    remove(): void;
    clear(callback: () => void): void;
    cache(value: any): void;
}

type Callback = () => void

export class Match implements MatchType {
    private _shouldRemove = false;
    get shouldRemove(): boolean {
        return this._shouldRemove;
    }

    /**
     * The element represented by the match will be removed from the document.
     */
    remove(): void {
        this._shouldRemove = true;
    }

    private _shouldClear = false;
    private clearCallback: Callback | null = null;
    get shouldClear(): boolean {
        return this._shouldClear;
    }

    /**
     * The callback will be called during the evaluation. This is useful, if
     * the element has some styling applied that matches the format, but might
     * contain some styling above that.
     *
     * @example
     * If you want to match bold elements, `<span class="myclass" style="font-weight:bold"/>
     * should match via `clear`, but should not be removed, because it still
     * has a class applied, even if the `style` attribute is removed.
     *
     * @remarks
     * You can still call `match.remove()` in the callback
     */
    clear(callback: Callback): void {
        this._shouldClear = true;
        this.clearCallback = callback;
    }

    get matches(): boolean {
        return this.shouldRemove || this.shouldClear;
    }

    /**
     * @returns Whether the element represented by the match should be removed
     */
    evaluate(): boolean {
        if (this.shouldClear) {
            this.clearCallback!();
        }

        return this.shouldRemove;
    }

    /** TODO try typing this */
    cache: any | null = null;

    setCache(value: any): void {
        this.cache = value;
    }
}

export class FakeMatch implements MatchType {
    public value: boolean = false;

    remove(): void {
        this.value = true;
    }

    clear(): void {
        this.value = true;
    }

    cache(): void {
        // noop
    }
}
