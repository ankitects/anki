type ContextProperty<T> = import("../../sveltelib/context-property").ContextProperty<T>;
type LifecycleHooks<T> = import("../../sveltelib/lifecycle-hooks").LifecycleHooks<T>;
type NoteEditorAPI = import("../../editor/NoteEditor.svelte").NoteEditorAPI;

declare module "anki/NoteEditor.svelte" {
    export const context: ContextProperty<NoteEditorAPI>;
    export const lifecycle: LifecycleHooks<NoteEditorAPI>;
    export const instances: NoteEditorAPI[];
}
