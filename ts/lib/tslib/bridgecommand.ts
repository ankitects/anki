// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "./runtime-require";

declare global {
    interface Window {
        bridgeCommand<T>(command: string, callback?: (value: T) => void): void;
    }
}

/** HTML <a> tag pointing to a bridge command. */
export function bridgeLink(command: string, label: string): string {
    return `<a href="javascript:bridgeCommand('${command}')">${label}</a>`;
}

export function bridgeCommandsAvailable(): boolean {
    return !!window.bridgeCommand;
}

export function bridgeCommand<T>(command: string, callback?: (value: T) => void): void {
    window.bridgeCommand<T>(command, callback);
}

registerPackage("anki/bridgecommand", {
    bridgeCommand,
});
