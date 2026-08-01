// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export class ChangeTimer {
    private value: number | null = null;
    private action: (() => Promise<void>) | null = null;
    private running: Promise<void> | null = null;

    constructor() {
        this.fireImmediately = this.fireImmediately.bind(this);
    }

    schedule(action: () => Promise<void>, delay: number): void {
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

    async fireImmediately(): Promise<void> {
        if (this.running) {
            // wait for the run in flight, then flush anything scheduled since
            await this.running;
        }
        this.clear();
        // take the action before running it; one scheduled during the await
        // below would otherwise be discarded when the run finished
        const action = this.action;
        this.action = null;
        if (action) {
            this.running = action().finally(() => {
                this.running = null;
            });
            await this.running;
        }
    }
}
