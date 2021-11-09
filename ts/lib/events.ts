// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function on<T extends EventTarget, L extends EventListener>(
    target: T,
    eventType: string,
    listener: L,
    options: AddEventListenerOptions = {},
): () => void {
    target.addEventListener(eventType, listener, options);
    return () => target.removeEventListener(eventType, listener, options);
}
