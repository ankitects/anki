export {};

declare global {
    namespace fabric {
        interface Object {
            id: string;
            ordinal: number;
            /** a custom property set on groups in the ungrouping routine to avoid adding a spurious undo entry */
            destroyed: boolean;
        }
    }
}
