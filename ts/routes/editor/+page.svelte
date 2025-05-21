<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import { setupEditor } from "./base";
    import * as base from "./base";
    import { page } from "$app/state";
    import type { EditorMode } from "./types";
    import { editorReady } from "@generated/backend";
    import { globalExport } from "@tslib/globals";

    const mode = (page.url.searchParams.get("mode") ?? "add") as EditorMode;

    globalExport(base);

    onMount(() => {
        setupEditor(mode).then(() => {
            editorReady({});
        });
    });
</script>
