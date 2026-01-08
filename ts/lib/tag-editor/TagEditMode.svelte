<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import TagDeleteBadge from "./TagDeleteBadge.svelte";
    import TagWithTooltip from "./TagWithTooltip.svelte";

    export let name: string;
    let className: string = "";
    export { className as class };

    export let tooltip: string;

    export let selected: boolean;
    export let active: boolean;
    export let shorten: boolean;
    export let truncateMiddle: boolean = false;
    export let editorWidth: number = 0;

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
    {truncateMiddle}
    {editorWidth}
    {flash}
    on:tagrange
    on:tagselect
    on:tagclick={() => dispatch("tagedit")}
    let:selectMode
    let:hoverClass
>
    <TagDeleteBadge
        class={hoverClass}
        on:click={(evt) => {
            if (!selectMode) {
                deleteTag();
                evt.stopPropagation();
            }
        }}
    />
</TagWithTooltip>
