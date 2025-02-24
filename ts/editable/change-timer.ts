// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export class ChangeTimer {
    private value: number | null = null;
    private action: (() => void) | null = null;

    constructor() {
        this.fireImmediately = this.fireImmediately.bind(this);
    }

    schedule(action: () => void, delay: number): void {
        this.clear();
        this.action = action;
        this.value = setTimeout(this.fireImmediately, delay) as any;
    }

    clear(): void {
        if (this.value) {
            clearTimeout(this.value);
            this.value = null;
        }
    }

    fireImmediately(): void {
        if (this.action) {
            this.action();
            this.action = null;
        }

        this.clear();
    }
}
