<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import { CodeMirror as CodeMirrorLib } from "./code-mirror";

    export interface CodeMirrorAPI {
        readonly editor: CodeMirrorLib.EditorFromTextArea;
    }
</script>

<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import type { Writable } from "svelte/store";
    import storeSubscribe from "../sveltelib/store-subscribe";

    export let configuration: CodeMirror.EditorConfiguration;
    export let code: Writable<string>;

    let codeMirror: CodeMirror.EditorFromTextArea;

    function setValue(content: string): void {
        codeMirror.setValue(content);
    }

    const { subscribe, unsubscribe } = storeSubscribe(code, setValue, false);
    const dispatch = createEventDispatcher();

    function openCodeMirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirrorLib.fromTextArea(textarea, configuration);

        // TODO passing in the tabindex option does not do anything: bug?
        codeMirror.getInputField().tabIndex = 0;

        codeMirror.on("change", () => dispatch("change", codeMirror.getValue()));
        codeMirror.on("focus", () => {
            unsubscribe();
            dispatch("focus");
        });
        codeMirror.on("blur", () => {
            subscribe();
            dispatch("blur");
        });

        subscribe();
    }

    export const api = Object.create(
        {},
        {
            editor: { get: () => codeMirror },
        },
    ) as CodeMirrorAPI;
</script>

<div class="code-mirror">
    <textarea tabindex="-1" hidden use:openCodeMirror />
</div>
