<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../components/IconButton.svelte";
    import WithShortcut from "../components/WithShortcut.svelte";
    import WithState from "../components/WithState.svelte";

    import { withButton } from "../components/helpers";
    import { appendInParentheses, execCommand, queryCommandState } from "./helpers";
    import { getNoteEditor } from "./OldEditorAdapter.svelte";

    export let key: string;
    export let tooltip: string;
    export let shortcut: string = "";

    export let withoutShortcut = false;
    export let withoutState = false;

    const { focusInRichText } = getNoteEditor();

    $: disabled = !$focusInRichText;
</script>

{#if withoutShortcut && withoutState}
    <IconButton {tooltip} {disabled} on:click={() => execCommand(key)}>
        <slot />
    </IconButton>
{:else if withoutShortcut}
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
                execCommand(key);
                updateState(event);
            }}
        >
            <slot />
        </IconButton>
    </WithState>
{:else if withoutState}
    <WithShortcut {shortcut} let:createShortcut let:shortcutLabel>
        <IconButton
            tooltip={appendInParentheses(tooltip, shortcutLabel)}
            {disabled}
            on:click={() => execCommand(key)}
            on:mount={withButton(createShortcut)}
        >
            <slot />
        </IconButton>
    </WithShortcut>
{:else}
    <WithShortcut {shortcut} let:createShortcut let:shortcutLabel>
        <WithState
            {key}
            update={async () => queryCommandState(key)}
            let:state={active}
            let:updateState
        >
            <IconButton
                tooltip={appendInParentheses(tooltip, shortcutLabel)}
                {active}
                {disabled}
                on:click={(event) => {
                    execCommand(key);
                    updateState(event);
                }}
                on:mount={withButton(createShortcut)}
            >
                <slot />
            </IconButton>
        </WithState>
    </WithShortcut>
{/if}
