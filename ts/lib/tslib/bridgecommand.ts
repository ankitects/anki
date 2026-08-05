// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "@tslib/events";
import { registerPackage } from "./runtime-require";

declare global {
    interface Window {
        bridgeCommand<T>(command: string, callback?: (value: T) => void): void;
    }
}

/** HTML <a> tag pointing to a bridge command via a `data-bridge-command` attribute.
 * Use `registerBridgeLinkHandler()` to register a click handler.
 */
export function bridgeLink(command: string, label: string): string {
    return `<a href="#" data-bridge-command="${command}">${label}</a>`;
}

export function registerBridgeLinkHandler<T extends Document | HTMLElement>(target: T) {
    const cleanup = on(target, "click", (event) => {
        if (event.target instanceof HTMLAnchorElement && event.target.dataset.bridgeCommand) {
            bridgeCommand(event.target.dataset.bridgeCommand);
            return false;
        }
        return true;
    });

    return cleanup;
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
