<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import TagWithTooltip from "./TagWithTooltip.svelte";
    import TagDeleteBadge from "./TagDeleteBadge.svelte";

    import { createEventDispatcher } from "svelte";

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
</script>

<TagWithTooltip
    {name}
    class={className}
    {tooltip}
    {selected}
    {active}
    {shorten}
    {flash}
    on:tagrange
    on:tagselect
    on:tagclick={() => dispatch("tagedit")}
    let:selectMode
    let:hoverClass
>
    <TagDeleteBadge
        class={hoverClass}
        on:click={() => {
            if (!selectMode) {
                deleteTag();
            }
        }}
    />
</TagWithTooltip>
