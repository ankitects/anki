<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import WithTooltip from "components/WithTooltip.svelte";
    import Tag from "./Tag.svelte";
    import TagDeleteBadge from "./TagDeleteBadge.svelte";

    import { createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";
    import { controlPressed, shiftPressed } from "lib/keys";
    import { delimChar } from "./tags";

    export let name: string;
    let className: string = "";
    export { className as class };

    export let tooltip: string;

    export let selected: boolean;
    export let active: boolean;
    export let shorten: boolean;

    export let flash: () => void;

    const dispatch = createEventDispatcher();

    function deleteTag(): void {
        dispatch("tagdelete");
    }

    let control = false;
    let shift = false;

    $: selectMode = control || shift;

    function setDeleteIcon(event: KeyboardEvent | MouseEvent): void {
        control = controlPressed(event);
        shift = shiftPressed(event);
    }

    function onClick(): void {
        if (shift) {
            dispatch("tagrange");
        } else if (control) {
            dispatch("tagselect");
        } else {
            dispatch("tagedit");
        }
    }

    function processTagName(name: string): string {
        const parts = name.split(delimChar);

        if (parts.length === 1) {
            return name;
        }

        return `…${delimChar}` + parts[parts.length - 1];
    }

    function hasMultipleParts(name: string): boolean {
        return name.split(delimChar).length > 1;
    }

    function onClickDelete() {
        if (!selectMode) {
            deleteTag();
        }
    }

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<svelte:body on:keydown={setDeleteIcon} on:keyup={setDeleteIcon} />

<div class:select-mode={selectMode} class:night-mode={nightMode}>
    {#if active}
        <Tag class={className} on:mousemove={setDeleteIcon} on:click={onClick}>
            {name}
            <TagDeleteBadge class="delete-icon" on:click={onClickDelete} />
        </Tag>
    {:else if shorten && hasMultipleParts(name)}
        <WithTooltip {tooltip} trigger="hover" placement="auto" let:createTooltip>
            <Tag
                class={className}
                bind:flash
                bind:selected
                on:mousemove={setDeleteIcon}
                on:click={onClick}
                on:mount={(event) => createTooltip(event.detail.button)}
            >
                <span>{processTagName(name)}</span>
                <TagDeleteBadge class="delete-icon" on:click={onClickDelete} />
            </Tag>
        </WithTooltip>
    {:else}
        <Tag
            class={className}
            bind:flash
            bind:selected
            on:mousemove={setDeleteIcon}
            on:click={onClick}
        >
            <span>{name}</span>
            <TagDeleteBadge class="delete-icon" on:click={onClickDelete} />
        </Tag>
    {/if}
</div>

<style lang="scss">
    .select-mode :global(button) {
        display: contents;
        cursor: crosshair;

        :global(.delete-icon) {
            opacity: 0;
        }
    }

    :global(.delete-icon):hover {
        $white-translucent: rgba(255 255 255 / 0.5);
        $dark-translucent: rgba(0 0 0 / 0.2);

        div & {
            background-color: $dark-translucent;
        }

        .night-mode & {
            background-color: $white-translucent;
        }
    }
</style>
