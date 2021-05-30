<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let title: string;
    export let subtitle: string | null = null;
</script>

<div class="graph" tabindex="-1">
    <h1>{title}</h1>

    {#if subtitle}
        <div class="subtitle">{subtitle}</div>
    {/if}

    <slot />
</div>

<style lang="scss">
    .graph {
        margin-left: auto;
        margin-right: auto;
        max-width: 60em;
        page-break-inside: avoid;

        /* See graph-styles.ts for constants referencing global styles */
        :global(.graph-element-clickable) {
            cursor: pointer;
        }

        /* Customizing the standard x and y tick markers and text on the graphs.
         * The `tick` class is automatically added by d3. */
        :global(.tick) {
            :global(line) {
                opacity: 0.1;
            }

            :global(text) {
                opacity: 0.5;
                font-size: 10px;

                @media only screen and (max-width: 800px) {
                    font-size: 13px;
                }

                @media only screen and (max-width: 600px) {
                    font-size: 16px;
                }
            }
        }

        :global(.tick-odd) {
            @media only screen and (max-width: 600px) {
                /* on small screens, hide every second row on graphs that have
                 * marked the ticks as odd */
                display: none;
            }
        }

        &:focus {
            outline: 0;
        }
    }

    h1 {
        text-align: center;
        margin-bottom: 0.25em;
        margin-top: 1.5em;
        font-weight: bold;
    }

    .subtitle {
        text-align: center;
        margin-bottom: 1em;
    }
</style>
