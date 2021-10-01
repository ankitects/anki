<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import IconButton from "../components/IconButton.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import WithShortcut from "../components/WithShortcut.svelte";
    import WithContext from "../components/WithContext.svelte";
    import WithState from "../components/WithState.svelte";

    import * as contextKeys from "../components/context-keys";
    import * as editorContextKeys from "./context-keys";

    export const components = {
        IconButton,
        LabelButton,
        WithShortcut,
        WithContext,
        WithState,
        contextKeys: { ...contextKeys, ...editorContextKeys },
    };
</script>
