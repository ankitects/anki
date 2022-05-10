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

    function setConstrainedSize(image: HTMLImageElement): void {
        delete image.dataset.editorFullsize;
        return;
        const aspectRatio = image.naturalWidth / image.naturalHeight;

        if (restrictionAspectRatio - aspectRatio > 1) {
            // restricted by height
            console.log("restrictedByHeight");
            /* rule.style.setProperty("width", "auto", "important"); */

            const width = Number(image.getAttribute("width")) || image.width;
            const height = Number(image.getAttribute("height")) || width / aspectRatio;
            /* rule.style.setProperty( */
            /*     "height", */
            /*     height < maxHeight ? `${height}px` : "auto", */
            /*     "important", */
            /* ); */
        } else {
            // square or restricted by width
            console.log("restrictedByWidth");
            const width = Number(image.getAttribute("width")) || image.width;
            /* rule.style.setProperty( */
            /*     "width", */
            /*     width < maxWidth ? `${width}px` : "auto", */
            /*     "important", */
            /* ); */

            /* rule.style.setProperty("height", "auto", "important"); */
        }
    }

    function setActualSize(image: HTMLImageElement): void {
        image.dataset.editorFullsize = "true";
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
