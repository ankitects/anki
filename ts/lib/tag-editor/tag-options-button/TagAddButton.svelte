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
    import { addTagIcon, tagIcon } from "$lib/components/icons";
    import Shortcut from "$lib/components/Shortcut.svelte";

    import { currentTagInput } from "../TagInput.svelte";

    export let keyCombination: string;

    const dispatch = createEventDispatcher<{ tagappend: null }>();

    function appendTag() {
        dispatch("tagappend");
    }
</script>

<!-- toggle tabindex to allow Tab to move focus to the tag editor from the last field -->
<!-- and allow Shift+Tab to move focus to the last field while inputting tag -->
<!-- svelte-ignore a11y-click-events-have-key-events -->
<div
    class="tag-add-button"
    title="{tr.editingTagsAdd()} ({getPlatformString(keyCombination)})"
    role="button"
    tabindex={$currentTagInput ? -1 : 0}
    on:click={appendTag}
    on:focus={appendTag}
>
    <IconConstrain>
        <Icon icon={tagIcon} />
        <Icon icon={addTagIcon} />
    </IconConstrain>
    {#if $$slots.default}
        <span class="tags-info">
            <slot />
        </span>
    {/if}
</div>

<Shortcut {keyCombination} on:action={() => dispatch("tagappend")} />

<style lang="scss">
    .tag-add-button {
        line-height: 1;

        :global(svg:last-child) {
            display: none;
        }

        &:hover {
            :global(svg:first-child) {
                display: none;
            }

            :global(svg:last-child) {
                display: block;
            }
        }

        :global(svg) {
            padding-bottom: 2px;
            cursor: pointer;
            fill: currentColor;
            opacity: 0.6;
        }

        :global(svg:hover) {
            opacity: 1;
        }
        .tags-info {
            cursor: pointer;
            color: var(--fg-subtle);
            margin-left: 0.75rem;
        }
    }
    :global([dir="rtl"]) .tags-info {
        margin-right: 0.75rem;
    }
</style>
