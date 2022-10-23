<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let title: string;
</script>

<div class="container" style:--gutter-block="2px" style:--container-margin="0">
    <div class="position-relative">
        <h1>{title}</h1>
        <div class="help-badge position-absolute"><slot name="tooltip" /></div>
    </div>
    <slot />
</div>

<style lang="scss">
    @use "sass/elevation" as *;
    .container {
        width: 100%;
        border-radius: var(--border-radius-large, 15px);
        padding: 1rem 1.75rem 0.75rem 1.25rem;
        border: var(--border-subtle);
        @include elevation(5);
        &:hover,
        &:focus-within {
            .help-badge {
                color: var(--fg-subtle);
            }
        }
    }
    h1 {
        border-bottom: 1px solid var(--border);
    }
    .help-badge {
        right: 0;
        bottom: 4px;
        color: var(--fg-faint);
        transition: color 0.2s linear;
        &:hover {
            color: var(--fg);
        }
    }
</style>
