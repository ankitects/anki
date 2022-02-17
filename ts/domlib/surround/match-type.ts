// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface MatchType {
    remove(): void;
    clear(callback: () => void): void;
    setCache(value: any): void;
}

type Callback = () => void;

export class Match implements MatchType {
    private _matches = false;
    get matches(): boolean {
        return this._matches;
    }

    private _shouldRemove = false;
    get shouldRemove(): boolean {
        return this._shouldRemove;
    }

    /**
     * The element represented by the match will be removed from the document.
     */
    remove(): void {
        this._matches = true;
        this._shouldRemove = true;
    }

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
    clear(callback: Callback): void {
        this._matches = true;
        callback();
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

    setCache(): void {
        // noop
    }
}
