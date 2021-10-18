export {};

declare global {
    interface DocumentOrShadowRoot {
        getSelection(): Selection | null;
    }

    interface Node {
        getRootNode(options?: GetRootNodeOptions): DocumentOrShadowRoot;
    }
}
