<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { marked } from "marked";

    import Badge from "../components/Badge.svelte";
    import WithTooltip from "../components/WithTooltip.svelte";
    import { infoCircle } from "./icons";
    import Label from "./Label.svelte";

    export let markdownTooltip: string;
    let forId: string | undefined = undefined;
    export { forId as for };
</script>

<span>
    {#if forId}
        <Label for={forId}><slot /></Label>
    {:else}
        <slot />
    {/if}
    <WithTooltip
        tooltip={marked(markdownTooltip)}
        showDelay={250}
        offset={[0, 20]}
        placement="bottom"
        let:createTooltip
    >
        <Badge
            class="opacity-50"
            iconSize={85}
            on:mount={(event) => createTooltip(event.detail.span)}
        >
            {@html infoCircle}
        </Badge>
    </WithTooltip>
</span>
