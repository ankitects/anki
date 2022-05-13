<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import { createEventDispatcher } from "svelte";

    import DropdownDivider from "../../components/DropdownDivider.svelte";
    import DropdownItem from "../../components/DropdownItem.svelte";
    import IconConstrain from "../../components/IconConstrain.svelte";
    import Popover from "../../components/Popover.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import {
        closeIcon,
        discardIcon,
        hsplitIcon,
        moreIcon,
        saveFillIcon as saveIcon,
        vsplitIcon,
    } from "./icons";

    export let closeable = true;

    const dispatch = createEventDispatcher();

    function onClose() {
        dispatch("close");
    }
</script>

<div class="pane-header" draggable="true">
    <span class="pane-label">
        <slot />
    </span>

    <span class="pane-icons">
        <span class="pane-icon">
            <IconConstrain iconSize={90} top={-2}>
                {@html discardIcon}
            </IconConstrain>
        </span>

        <span class="pane-icon rainbow-icon">
            <IconConstrain iconSize={90} top={-2}>
                {@html saveIcon}
            </IconConstrain>
        </span>

        <WithFloating placement="bottom-end" closeOnInsideClick let:toggle>
            <span slot="reference" class="pane-icon" on:click={toggle}>
                <IconConstrain iconSize={90} top={-2}>
                    {@html moreIcon}
                </IconConstrain>
            </span>

            <Popover slot="floating">
                <div class="pane-menu">
                    <DropdownItem on:click={() => dispatch("vsplit")}>
                        <IconConstrain>
                            {@html vsplitIcon}
                        </IconConstrain>
                        <span style:margin-left="5px">Split vertical</span>
                    </DropdownItem>
                    <DropdownItem on:click={() => dispatch("hsplit")}>
                        <IconConstrain>
                            {@html hsplitIcon}
                        </IconConstrain>
                        <span style:margin-left="5px">Split horizontal</span>
                    </DropdownItem>
                    <DropdownDivider />
                    <DropdownItem>Other...</DropdownItem>
                </div>
            </Popover>
        </WithFloating>

        {#if closeable}
            <span class="pane-icon" on:click={onClose}>
                <IconConstrain top={-2}>
                    {@html closeIcon}
                </IconConstrain>
            </span>
        {/if}
    </span>
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .pane-header {
        display: inline-flex;

        font-size: 16px;
        font-weight: 600;
        line-height: 32px;

        padding: 0px 10px;
        background-color: lightgreen;

        @include elevation.elevation(2);
        z-index: 25;
    }

    .pane-menu {
        font-size: 12px;
        display: flex;
        flex-flow: column nowrap;
    }

    .pane-icons {
        margin-left: auto;

        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }

    .pane-label {
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }

    .pane-icon {
        cursor: pointer;
    }

    .rainbow-icon :global(svg) {
        animation: rainbow 4s ease infinite;
    }

    @keyframes rainbow {
        0% {
            fill: #ff2400;
        }
        14% {
            fill: #e81d1d;
        }
        28% {
            fill: #e8b71d;
        }
        42% {
            fill: #e3e81d;
        }
        56% {
            fill: #1de840;
        }
        60% {
            fill: #1ddde8;
        }
        74% {
            fill: #2b1de8;
        }
        88% {
            fill: #dd00f3;
        }
        100% {
            fill: #ff2400;
        }
    }
</style>
