<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher } from "svelte";

    import Icon from "$lib/components/Icon.svelte";
    import IconConstrain from "$lib/components/IconConstrain.svelte";
    import { mdiEye } from "$lib/components/icons";

    export let full: boolean = false;
    export let keyCombination: string = "Control+.";

    const dispatch = createEventDispatcher<{ displaymodechange: null }>();

    function toggle() {
        dispatch("displaymodechange");
    }

    $: title = `${tr.editingTagDisplayToggle()} (${getPlatformString(keyCombination)})`;
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<div
    class="tag-display-mode-button"
    class:active={full}
    {title}
    role="button"
    tabindex="-1"
    on:click={toggle}
>
    <IconConstrain>
        <Icon icon={mdiEye} />
    </IconConstrain>
</div>

<style lang="scss">
    .tag-display-mode-button {
        line-height: 1;
        margin-left: 4px;

        :global(svg) {
            padding-bottom: 2px;
            cursor: pointer;
            fill: currentColor;
            opacity: 0.4;
        }

        &:hover :global(svg),
        &.active :global(svg) {
            opacity: 1;
        }
    }
</style>
