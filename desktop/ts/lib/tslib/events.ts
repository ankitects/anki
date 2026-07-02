// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type EventTargetToMap<A extends EventTarget> = A extends HTMLElement ? HTMLElementEventMap
    : A extends Document ? DocumentEventMap
    : A extends Window ? WindowEventMap
    : A extends FileReader ? FileReaderEventMap
    : A extends Animation ? AnimationEventMap
    : A extends EventSource ? EventSourceEventMap
    : A extends AbortSignal ? AbortSignalEventMap
    : A extends AbstractWorker ? AbstractWorkerEventMap
    : never;

export function on<T extends EventTarget, K extends keyof EventTargetToMap<T>>(
    target: T,
    eventType: Exclude<K, symbol | number>,
    handler: (this: T, event: EventTargetToMap<T>[K]) => void,
    options?: AddEventListenerOptions,
): () => void {
    target.addEventListener(eventType, handler as EventListener, options);
    return () => target.removeEventListener(eventType, handler as EventListener, options);
}

export function preventDefault(event: Event): void {
    event.preventDefault();
}
