export {};

declare global {
    interface DocumentOrShadowRoot {
        getSelection(): Selection | null;
    }
}
