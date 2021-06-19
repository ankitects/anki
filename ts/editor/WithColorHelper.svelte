<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { colorHelperIcon } from "./icons";

    export let color: string;

    function setColor({ currentTarget }: Event): string {
        return (color = (currentTarget! as HTMLInputElement).value);
    }
</script>

<div style={`--color-helper-color: ${color}`}>
    <slot {colorHelperIcon} {setColor} />
</div>

<style lang="scss">
    div {
        display: contents;

        :global(#mdi-color-helper) {
            fill: var(--color-helper-color);
        }
    }
</style>
