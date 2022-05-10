<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    export let maxWidth: number;
    export let maxHeight: number;

    let active: boolean = false;

    $: restrictionAspectRatio = maxWidth / maxHeight;

    const dispatch = createEventDispatcher();

    function setConstrainedWidth(image: HTMLImageElement): void {
        const naturalWidth = image.naturalWidth;
        const naturalHeight = image.naturalHeight;
        const aspectRatio = naturalWidth / naturalHeight;

        let constrainedWidth: number;

        if (restrictionAspectRatio - aspectRatio > 1) {
            // Constrained by height
            constrainedWidth = (maxHeight / naturalHeight) * naturalWidth;
        } else {
            // Square or constrained by width
            constrainedWidth = maxWidth;
        }

        const width = Number(image.getAttribute("width")) || image.width;

        if (constrainedWidth >= width) {
            // Image was resized to be smaller than the constrained size would be
            constrainedWidth = width;
        }

        image.style.setProperty("--editor-width", `${Math.round(constrainedWidth)}px`);
    }

    function setConstrainedSize(image: HTMLImageElement): void {
        image.dataset.editorSizeConstrained = "true";
        setConstrainedWidth(image);
    }

    function setActualSize(image: HTMLImageElement): void {
        delete image.dataset.editorSizeConstrained;
        image.style.removeProperty("--editor-width");
    }

    function toggleActualSize(image: HTMLImageElement) {
        active = !active;

        if (active) {
            setActualSize(image);
        } else {
            setConstrainedSize(image);
        }

        dispatch("update", active);
    }
</script>

<slot {toggleActualSize} {active} />
