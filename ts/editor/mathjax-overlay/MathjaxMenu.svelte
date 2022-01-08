<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Shortcut from "../../components/Shortcut.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import MathjaxEditor from "./MathjaxEditor.svelte";
    import MathjaxButtons from "./MathjaxButtons.svelte";
    import type { Writable } from "svelte/store";
    import { createEventDispatcher } from "svelte";
    import { placeCaretAfter } from "../../domlib/place-caret";

    export let element: Element;
    export let code: Writable<string>;
    export let selectAll: boolean;

    const acceptShortcut = "Enter";
    const newlineShortcut = "Shift+Enter";

    function appendNewline(): void {
        code.update((value) => `${value}\n`);
    }

    export let updateSelection: () => Promise<void>;
    let dropdownApi: any;

    export async function update() {
        await updateSelection?.();
        dropdownApi.update();
    }

    const dispatch = createEventDispatcher();
</script>

<div class="mathjax-menu">
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
        <Shortcut keyCombination={newlineShortcut} on:action={appendNewline} />

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
</style>
