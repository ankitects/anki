<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import IconButton from "components/IconButton.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import WithState from "components/WithState.svelte";
    import OnlyEditable from "./OnlyEditable.svelte";

    import { appendInParentheses } from "./helpers";

    export let key: string;
    export let tooltip: string;
    export let shortcut: string = "";

    export let withoutShortcut = false;
    export let withoutState = false;
</script>

<OnlyEditable let:disabled>
    {#if withoutShortcut && withoutState}
        <IconButton {tooltip} {disabled} on:click={() => document.execCommand(key)}>
            <slot />
        </IconButton>
    {:else if withoutShortcut}
        <WithState
            {key}
            update={() => document.queryCommandState(key)}
            let:state={active}
            let:updateState
        >
            <IconButton
                {tooltip}
                {active}
                {disabled}
                on:click={(event) => {
                    document.execCommand(key);
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
                on:click={() => document.execCommand(key)}
                on:mount={(event) => createShortcut(event.detail.button)}
            >
                <slot />
            </IconButton>
        </WithShortcut>
    {:else}
        <WithShortcut {shortcut} let:createShortcut let:shortcutLabel>
            <WithState
                {key}
                update={() => document.queryCommandState(key)}
                let:state={active}
                let:updateState
            >
                <IconButton
                    tooltip={appendInParentheses(tooltip, shortcutLabel)}
                    {active}
                    {disabled}
                    on:click={(event) => {
                        document.execCommand(key);
                        updateState(event);
                    }}
                    on:mount={(event) => createShortcut(event.detail.button)}
                >
                    <slot />
                </IconButton>
            </WithState>
        </WithShortcut>
    {/if}
</OnlyEditable>
