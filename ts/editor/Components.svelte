<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import { iconButton } from "components/dynamic-components";

    import {
        withContext,
        withState,
        withShortcut,
        onClick,
        onMount,
        slottedHtml,
    } from "addons/dynamic-components";

    import * as contextKeys from "components/context-keys";
    import * as editorContextKeys from "./context-keys";

    export const components = {
        iconButton,
        withContext,
        withState,
        withShortcut,
        onClick,
        onMount,
        slottedHtml,
        contextKeys: { ...contextKeys, ...editorContextKeys },
    };
</script>
