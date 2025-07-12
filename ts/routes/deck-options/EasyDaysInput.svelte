<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import * as tr from "@generated/ftl";
    import Item from "$lib/components/Item.svelte";

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
</script>

<Item>
    <div class="container">
        <div class="easy-days-settings">
            <span></span>
            <span class="header min-col">{tr.deckConfigEasyDaysMinimum()}</span>
            <span class="header">{tr.deckConfigEasyDaysReduced()}</span>
            <span class="header normal-col">{tr.deckConfigEasyDaysNormal()}</span>

            {#each easyDays as day, index}
                <span class="day">{day}</span>
                <div class="input-container">
                    <input
                        type="range"
                        bind:value={values[index]}
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
    }

    .day {
        word-wrap: break-word;
        font-size: smaller;
    }
</style>
