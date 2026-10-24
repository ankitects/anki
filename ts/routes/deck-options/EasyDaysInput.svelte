<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import Item from "$lib/components/Item.svelte";
    import { onMount } from "svelte";

    const easyDays = [
        tr.deckConfigEasyDaysMonday(),
        tr.deckConfigEasyDaysTuesday(),
        tr.deckConfigEasyDaysWednesday(),
        tr.deckConfigEasyDaysThursday(),
        tr.deckConfigEasyDaysFriday(),
        tr.deckConfigEasyDaysSaturday(),
        tr.deckConfigEasyDaysSunday(),
    ];

    export let values = [0, 0, 0, 0, 0, 0, 0];

    let startY = 0;
    let originalValues = [...values];

    function handleTouchStart(): void {
        startY = window.scrollY;
        originalValues = [...values];
    }

    function handleTouchEnd(): void {
        const deltaY = Math.abs(window.scrollY - startY);
        if (deltaY > 1) {
            // If the screen has scrolled
            values = [...originalValues];
        }
    }

    function onInput(event: Event, index: number): void {
        values[index] = parseFloat((event.target as HTMLInputElement).value);
    }

    let touchDivRef: HTMLDivElement | null = null;

    // Todo: Use on:touchstart. For some reason it doesn't work when added to the div in the markup, but works when added manually here.
    onMount(() => {
        touchDivRef?.addEventListener("touchstart", handleTouchStart, {
            passive: true,
        });
        touchDivRef?.addEventListener("touchend", handleTouchEnd, { passive: true });

        return () => {
            touchDivRef?.removeEventListener("touchstart", handleTouchStart);
            touchDivRef?.removeEventListener("touchend", handleTouchEnd);
        };
    });
</script>

<Item>
    <div class="container" bind:this={touchDivRef}>
        <div class="easy-days-settings">
            <span></span>
            <span class="header min-col">{tr.deckConfigEasyDaysMinimum()}</span>
            <span class="header">{tr.deckConfigEasyDaysReduced()}</span>
            <span class="header normal-col">{tr.deckConfigEasyDaysNormal()}</span>

            {#each easyDays as day, index}
                <span class="day">{day}</span>
                <div class="input-container">
                    <input
                        on:input={(e) => onInput(e, index)}
                        type="range"
                        value={values[index]}
                        step={0.5}
                        max={1.0}
                        min={0.0}
                        list="easy_day_steplist"
                    />
                </div>
            {/each}
        </div>
    </div>
</Item>

<style lang="scss">
    .container {
        display: flex;
        justify-content: center;
    }
    .easy-days-settings {
        width: 100%;
        max-width: 1000px;
        border-collapse: collapse;

        display: grid;
        grid-template-columns: auto 1fr 1fr 1fr;

        border-collapse: collapse;
        & > * {
            padding: 8px 16px;
            border-bottom: var(--border) solid 1px;
        }
    }
    .input-container {
        grid-column: 2 / span 3;
    }
    span {
        display: flex;
        align-items: center;
        justify-content: center;

        &.min-col {
            justify-content: flex-start;
        }

        &.normal-col {
            justify-content: flex-end;
        }
    }
    .header {
        word-wrap: break-word;
        font-size: smaller;
    }
    .easy-days-settings input[type="range"] {
        width: 100%;
        cursor: pointer;
        touch-action: auto;
    }

    .day {
        word-wrap: break-word;
        font-size: smaller;
    }
</style>
