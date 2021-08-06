// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export class ChangeTimer {
    private value: number | null = null;

    schedule(action: () => void, delay: number) {
        this.clear();
        this.value = setTimeout(action, delay);
    }

    clear() {
        if (this.value) {
            clearTimeout(this.value);
            this.value = null;
        }
    }
}
