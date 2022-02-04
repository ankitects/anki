<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import WithTooltip from "../components/WithTooltip.svelte";
    import { descriptionIcon } from "./icons";

    export let description: string;
</script>

<span>
    <WithTooltip
        tooltip={description}
        showDelay={250}
        offset={[0, 20]}
        placement="right"
        let:createTooltip
    >
        <Badge
            --icon-align="sub"
            iconSize={65}
            on:mount={(event) => createTooltip(event.detail.span)}
        >
            {@html descriptionIcon}
        </Badge>
    </WithTooltip>
</span>

<style lang="scss">
    span {
        opacity: 0.4;

        &:hover {
            opacity: 0.8;
        }
    }
</style>
