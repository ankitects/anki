<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import WithTooltip from "components/WithTooltip.svelte";
    import Tag from "./Tag.svelte";

    import { delimChar } from "./tags";

    export let name: string;
    let className: string = "";
    export { className as class };

    export let tooltip: string;

    export let selected: boolean;
    export let active: boolean;
    export let shorten: boolean;

    export let flash: () => void;

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
</script>

{#if active}
    <Tag class={className} {name} />
{:else if shorten && hasMultipleParts(name)}
    <WithTooltip {tooltip} placement="auto" let:createTooltip>
        <Tag
            class={className}
            name={processTagName(name)}
            bind:flash
            bind:selected
            on:tagedit
            on:tagselect
            on:tagrange
            on:tagdelete
            on:mount={(event) => createTooltip(event.detail.button)}
        />
    </WithTooltip>
{:else}
    <Tag
        class={className}
        {name}
        bind:flash
        bind:selected
        on:tagedit
        on:tagselect
        on:tagrange
        on:tagdelete
    />
{/if}
