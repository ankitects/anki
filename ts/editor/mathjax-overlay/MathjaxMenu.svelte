<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Shortcut from "../../components/Shortcut.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import HandleSelection from "../HandleSelection.svelte";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import MathjaxEditor from "./MathjaxEditor.svelte";
    import MathjaxButtons from "./MathjaxButtons.svelte";
    import type { Writable } from "svelte/store";
    import { createEventDispatcher } from "svelte";

    export let activeImage: HTMLImageElement;
    export let mathjaxElement: HTMLElement;
    export let container: HTMLElement;
    export let errorMessage: string;
    export let code: Writable<string>;

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
    <HandleSelection image={activeImage} {container} bind:updateSelection on:mount>
        <HandleBackground tooltip={errorMessage} />
        <HandleControl offsetX={1} offsetY={1} />
    </HandleSelection>

    <DropdownMenu>
        <MathjaxEditor
            {acceptShortcut}
            {newlineShortcut}
            {code}
            on:blur={() => dispatch("reset")}
        />

        <Shortcut keyCombination={acceptShortcut} on:action={() => dispatch("reset")} />
        <Shortcut keyCombination={newlineShortcut} on:action={appendNewline} />

        <MathjaxButtons
            {activeImage}
            {mathjaxElement}
            on:delete={() => dispatch("delete")}
        />
    </DropdownMenu>
</div>

<style lang="scss">
    .mathjax-menu :global(.dropdown-menu) {
        border-color: var(--border);
    }
</style>
