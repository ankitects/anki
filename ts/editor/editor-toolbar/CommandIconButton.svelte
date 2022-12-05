<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getPlatformString } from "@tslib/shortcuts";

    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import { execCommand, queryCommandState } from "../../domlib";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";

    export let key: string;
    export let tooltip: string;
    export let shortcut: string | null = null;

    $: theTooltip = shortcut ? `${tooltip} (${getPlatformString(shortcut)})` : tooltip;

    export let withoutState = false;

    const { focusedInput } = noteEditorContext.get();

    function action() {
        execCommand(key);
    }

    $: disabled = !$focusedInput || !editingInputIsRichText($focusedInput);
</script>

{#if withoutState}
    <IconButton tooltip={theTooltip} {disabled} on:click={action}>
        <slot />
    </IconButton>

    {#if shortcut}
        <Shortcut keyCombination={shortcut} on:action={action} />
    {/if}
{:else}
    <WithState
        {key}
        update={async () => queryCommandState(key)}
        let:state={active}
        let:updateState
    >
        <IconButton
            tooltip={theTooltip}
            {active}
            {disabled}
            on:click={(event) => {
                action();
                updateState(event);
            }}
        >
            <slot />
        </IconButton>

        {#if shortcut}
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
