<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as float from "@floating-ui/dom";

    export let floating: HTMLElement;

    interface PositionArgs {
        floating: HTMLElement;
    }

    function position(
        ref: HTMLElement,
        args: PositionArgs,
    ): { update(args: PositionArgs): void; destroy(): void } {
        function update({ floating }: PositionArgs): void {
            if (!floating) {
                return;
            }

            float
                .computePosition(ref, floating, {
                    placement: "bottom-start",
                })
                .then(({ x, y }) => {
                    Object.assign(floating.style, {
                        left: `${x}px`,
                        top: `${y}px`,
                    });
                });
        }

        update(args);

        return {
            destroy() {},
            update,
        };
    }
</script>

<div use:position={{ floating }}>
    <slot />
</div>
