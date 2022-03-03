<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
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
            on:blur={() => dispatch("reset")}
            on:moveoutstart
            on:moveoutend
        />

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
        />
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
