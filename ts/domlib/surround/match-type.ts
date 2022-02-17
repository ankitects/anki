// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface MatchType {
    remove(): void;
    clear(callback: () => void): void;
    cache(value: any): void;
}

export class Match implements MatchType {
    markedClear = false;
    clearCallback: (() => void) | null = null;

    markedRemove = false;

    /** TODO try typing this */
    cached: any | null = null;

    get marked(): boolean {
        return this.markedClear || this.markedRemove;
    }

    remove(): void {
        this.markedRemove = true;
    }

    clear(callback: () => void): void {
        this.markedClear = true;
        this.clearCallback = callback;
    }

    cache(value: any): void {
        this.cached = value;
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
