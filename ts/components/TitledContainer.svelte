<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let title: string;
</script>

<div
    {id}
    class="titled-container {className}"
    class:light={!$pageTheme.isDark}
    class:dark={$pageTheme.isDark}
    class:rtl
>
    <div class="position-relative">
        <h1>{title}</h1>
        <div class="help-badge position-absolute" class:rtl>
            <slot name="badge" />
        </div>
    </div>
    <slot />
</div>

<style lang="scss">
    @use "sass/elevation" as *;
    .titled-container {
        --gutter-block: 2px;
        --gutter-inline: 2px;

        display: flex;
        flex-direction: column;
        flex-grow: 1;
        width: 100%;
        height: 100%;
        background: var(--container-bg, var(--canvas-elevated));
        border: 1px solid var(--border-subtle);
        border-radius: var(--border-radius-large, 10px);
        padding: 1rem 1.75rem 0.75rem 1.25rem;
        &.rtl {
            padding: 1rem 1.25rem 0.75rem 1.75rem;
        }
        &:hover,
        &:focus-within {
            .help-badge {
                color: var(--fg-subtle);
            }
        }
        &.light {
            @include elevation(2, $opacity-boost: -0.08);
            &:hover,
            &:focus-within {
                @include elevation(3);
            }
        }
        &.dark {
            @include elevation(3, $opacity-boost: -0.08);
            &:hover,
            &:focus-within {
                @include elevation(4);
            }
        }
        transition: box-shadow 0.2s ease-in-out;
    }
    h1 {
        position: sticky;
        text-align: var(--text-align, start);
        border-bottom: 1px solid var(--border);
    }
    .help-badge {
        right: 0;
        bottom: 12px;
        color: var(--fg-faint);
        transition: color 0.2s linear;
        &:hover {
            transition: none;
            color: var(--fg);
        }
        &.rtl {
            right: unset;
            left: 0;
        }
    }
</style>
