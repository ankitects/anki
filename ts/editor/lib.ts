// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

declare global {
    interface Window {
        bridgeCommand<T>(command: string, callback?: (value: T) => void): void;
    }
}

export function bridgeCommand<T>(command: string, callback?: (value: T) => void): void {
    window.bridgeCommand<T>(command, callback);
}
