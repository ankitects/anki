<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { stepsToString, stringToSteps } from "./steps";

    export let value: number[];

    let stringValue: string;
    $: stringValue = stepsToString(value);

    function update(this: HTMLInputElement): void {
        value = stringToSteps(this.value);
    }
</script>

<input type="text" value={stringValue} on:blur={update} />

<style lang="scss">
    input {
        width: 100%;
    }
</style>
