<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import marked from "marked";
    import { infoCircle } from "./icons";
    import WithTooltip from "./WithTooltip.svelte";
    import Label from "./Label.svelte";
    import Badge from "./Badge.svelte";

    export let markdownTooltip: string;
    let forId: string;
    export { forId as for };
</script>

<span>
    <Label for={forId}><slot /></Label>
    <WithTooltip tooltip={marked(markdownTooltip)} let:createTooltip>
        <Badge
            class="opacity-50"
            on:mount={(event) => createTooltip(event.detail.span)}
        >
            {@html infoCircle}
        </Badge>
    </WithTooltip>
</span>
