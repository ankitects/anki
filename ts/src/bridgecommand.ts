// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// HTML <a> tag pointing to a bridge command.
export function bridgeLink(command: string, label: string): string {
    return `<a href="javascript:bridgeCommand('${command}')">${label}</a>`;
}
