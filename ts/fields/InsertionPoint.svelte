<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    export let active: boolean;

    let show = false;

    function onDragOver({ dataTransfer }: DragEvent): void {
        dataTransfer!.dropEffect = "move";
    }

    const dispatch = createEventDispatcher();

    function onDrop({ dataTransfer }: DragEvent): void {
        show = false;
        dispatch("insertion", dataTransfer!.getData("text/plain"));
    }
</script>

<div class="insertion-point-container">
    <div
        class="insertion-point"
        class:inactive={!active}
        class:insertion-point--active={show}
        on:dragenter|stopPropagation={() => (show = true)}
        on:dragleave|preventDefault|stopPropagation={() => (show = false)}
        on:dragover|preventDefault|stopPropagation={onDragOver}
        on:drop|preventDefault|stopPropagation={onDrop}
    >
        <div class="insertion-point-visual" />
    </div>
</div>

<style lang="scss">
    @use "sass:math" as math;

    .insertion-point-container {
        position: relative;
    }

    /* This is the height of the drop target */
    $insertion-height: 14px;

    .insertion-point {
        position: absolute;
        width: auto;
        height: $insertion-height;

        top: -#{math.div($insertion-height, 2)};
        bottom: 0;
        left: 0;
        right: 0;

        z-index: 5;

        &.inactive {
            pointer-events: none;
        }
    }

    $visual-cue-height: 4px;

    .insertion-point-visual {
        width: auto;
        height: $visual-cue-height;

        position: relative;
        top: math.div($insertion-height - $visual-cue-height, 2);

        pointer-events: none;

        .insertion-point--active & {
            background-color: #76d1c7;
        }
    }
</style>
