<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import { toggleEditorButton } from "../old-editor-adapter";
    import { singleCallback } from "@tslib/typing";
    import { on } from "@tslib/events";

    const { buttons } = $props<{ buttons: string[] }>();

    $effect(() => {
        // Each time the buttons are changed...
        buttons;

        // Add event handlers to each button
        const addonButtons = document.querySelectorAll(".anki-addon-button");
        const cbs = [...addonButtons].map((button) =>
            singleCallback(
                on(button, "click", () => {
                    const command = button.getAttribute("data-command");
                    if (command) {
                        bridgeCommand(command);
                    }
                    const toggleable = button.getAttribute("data-cantoggle");
                    if (toggleable === "1") {
                        toggleEditorButton(button as HTMLButtonElement);
                    }

                    return false;
                }),
                on(button as HTMLButtonElement, "mousedown", (evt) => {
                    evt.preventDefault();
                    evt.stopPropagation();
                }),
            ),
        );

        return singleCallback(...cbs);
    });

    const radius = "5px";
    function getBorderRadius(index: number, length: number): string {
        if (index === 0 && length === 1) {
            return `--border-left-radius: ${radius}; --border-right-radius: ${radius}; `;
        } else if (index === 0) {
            return `--border-left-radius: ${radius}; --border-right-radius: 0; `;
        } else if (index === length - 1) {
            return `--border-left-radius: 0; --border-right-radius: ${radius}; `;
        } else {
            return "--border-left-radius: 0; --border-right-radius: 0; ";
        }
    }
</script>

<ButtonGroup>
    {#each buttons as button, index}
        <div style={getBorderRadius(index, buttons.length)}>
            {@html button}
        </div>
    {/each}
</ButtonGroup>

<style lang="scss">
    div {
        display: contents;
    }
</style>
