<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";

    import { execCommand, queryCommandState } from "../helpers";
    import { getNoteEditor } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";

    export let key: string;
    export let tooltip: string;
    export let shortcut: string = "";

    export let withoutShortcut = false;
    export let withoutState = false;

    const { activeInput } = getNoteEditor();

    function action() {
        execCommand(key);
    }

    $: disabled = !editingInputIsRichText($activeInput);
</script>

{#if withoutState}
    <IconButton {tooltip} {disabled} on:click={action}>
        <slot />
    </IconButton>

    {#if !withoutShortcut}
        <Shortcut keyCombination={shortcut} on:click={action} />
    {/if}
{:else}
    <WithState
        {key}
        update={async () => queryCommandState(key)}
        let:state={active}
        let:updateState
    >
        <IconButton
            {tooltip}
            {active}
            {disabled}
            on:click={(event) => {
                action();
                updateState(event);
            }}
        >
            <slot />
        </IconButton>

        {#if !withoutShortcut}
            <Shortcut
                keyCombination={shortcut}
                on:action={(event) => {
                    action();
                    updateState(event);
                }}
            />
        {/if}
    </WithState>
{/if}
