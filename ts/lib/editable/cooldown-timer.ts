// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export class CooldownTimer {
    private executing = false;
    private queuedAction: (() => void) | null = null;
    private delay: number;

    constructor(delayMs: number) {
        this.delay = delayMs;
    }

    schedule(action: () => void): void {
        if (this.executing) {
            this.queuedAction = action;
        } else {
            this.executing = true;
            action();
            setTimeout(this.#pop.bind(this), this.delay);
        }
    }

    #pop(): void {
        this.executing = false;
        if (this.queuedAction) {
            const action = this.queuedAction;
            this.queuedAction = null;
            this.schedule(action);
        }
    }
}
