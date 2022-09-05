<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type CodeMirrorLib from "codemirror";
    import { createEventDispatcher } from "svelte";
    import type { Writable } from "svelte/store";

    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { placeCaretAfter } from "../../domlib/place-caret";
    import { pageTheme } from "../../sveltelib/theme";
    import MathjaxButtons from "./MathjaxButtons.svelte";
    import MathjaxEditor from "./MathjaxEditor.svelte";

    export let element: Element;
    export let code: Writable<string>;

    export let selectAll: boolean;
    export let position: CodeMirrorLib.Position | undefined;

    const acceptShortcut = "Enter";
    const newlineShortcut = "Shift+Enter";

    export let updateSelection: () => Promise<void>;
    let dropdownApi: any;

    export async function update() {
        await updateSelection?.();
        dropdownApi.update();
    }

    const dispatch = createEventDispatcher();
</script>

<div class="mathjax-menu" class:light-theme={!$pageTheme.isDark}>
    <slot />

    <DropdownMenu>
        <MathjaxEditor
            {acceptShortcut}
            {newlineShortcut}
            {code}
            {selectAll}
            {position}
            on:blur={() => dispatch("reset")}
            on:moveoutstart
            on:moveoutend
            let:editor={mathjaxEditor}
        >
            <Shortcut
                keyCombination={acceptShortcut}
                on:action={() => dispatch("moveoutend")}
            />

            <MathjaxButtons
                {element}
                on:delete={() => {
                    placeCaretAfter(element);
                    element.remove();
                    dispatch("reset");
                }}
                on:surround={async ({ detail }) => {
                    const editor = await mathjaxEditor.editor;
                    const { prefix, suffix } = detail;

                    editor.replaceSelection(prefix + editor.getSelection() + suffix);
                }}
            />
        </MathjaxEditor>
    </DropdownMenu>
</div>

<style lang="scss">
    .mathjax-menu :global(.dropdown-menu) {
        border-color: var(--border);
    }

    .light-theme {
        :global(.dropdown-menu) {
            background-color: var(--window-bg);
        }

        :global(.CodeMirror) {
            border-width: 1px 0;
            border-style: solid;
            border-color: var(--border);
        }
    }
</style>
