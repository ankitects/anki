<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher } from "svelte";

    import IconConstrain from "../../components/IconConstrain.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { addTagIcon, tagIcon } from "./icons";

    export let keyCombination: string;

    const dispatch = createEventDispatcher<{ tagappend: CustomEvent<void> }>();

    function appendTag() {
        dispatch("tagappend");
    }
</script>

<div
    class="tag-add-button"
    title="{tr.editingTagsAdd()} ({getPlatformString(keyCombination)})"
    tabindex={0}
    on:click={appendTag}
    on:focus={appendTag}
>
    <IconConstrain>
        {@html tagIcon}
        {@html addTagIcon}
    </IconConstrain>
    <span class="tags-info">
        <slot />
    </span>
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
