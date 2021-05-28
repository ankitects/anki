<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let value: number;
    export let min = 1;
    export let max = 9999;

    function checkMinMax() {
        if (value > max) {
            value = max;
        } else if (value < min) {
            value = min;
        }
    }
</script>

<input
    type="number"
    {min}
    {max}
    bind:value
    class="form-control"
    on:blur={checkMinMax}
/>
