<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let active: boolean;

    const dispatch = createEventDispatcher();
    const nightMode = getContext(nightModeKey);

    const onPointerdown =
        (north: boolean, west: boolean) =>
        (event: PointerEvent): void => {
            dispatch("pointerclick", { north, west, originalEvent: event });
        };
</script>

<div
    class:nightMode
    class:active
    class="nw"
    on:mousedown|preventDefault
    on:pointerdown={onPointerdown(true, true)}
    on:pointerup
    on:pointermove
/>
<div
    class:nightMode
    class:active
    class="ne"
    on:mousedown|preventDefault
    on:pointerdown={onPointerdown(true, false)}
    on:pointerup
    on:pointermove
/>
<div
    class:nightMode
    class:active
    class="sw"
    on:mousedown|preventDefault
    on:pointerdown={onPointerdown(false, true)}
    on:pointerup
    on:pointermove
/>
<div
    class:nightMode
    class:active
    class="se"
    on:mousedown|preventDefault
    on:pointerdown={onPointerdown(false, false)}
    on:pointerup
    on:pointermove
/>

<style lang="scss">
    div {
        position: absolute;

        width: 7px;
        height: 7px;
        border: 1px solid black;

        &.active {
            background-color: black;
        }

        &.nightMode {
            border-color: white;

            &.active {
                background-color: white;
            }
        }
    }

    .nw {
        top: -5px;
        left: -5px;
        border-bottom: none;
        border-right: none;

        &.active {
            cursor: nw-resize;
        }
    }

    .ne {
        top: -5px;
        right: -5px;
        border-bottom: none;
        border-left: none;

        &.active {
            cursor: ne-resize;
        }
    }

    .sw {
        bottom: -5px;
        left: -5px;
        border-top: none;
        border-right: none;

        &.active {
            cursor: sw-resize;
        }
    }

    .se {
        bottom: -5px;
        right: -5px;
        border-top: none;
        border-left: none;

        &.active {
            cursor: se-resize;
        }
    }
</style>
