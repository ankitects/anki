declare global {
    interface Window {
        bridgeCommand<T>(command: string, callback?: (value: T) => void): void;
    }
}

export function bridgeCommand<T>(command: string, callback?: (value: T) => void): void {
    window.bridgeCommand<T>(command, callback);
}
