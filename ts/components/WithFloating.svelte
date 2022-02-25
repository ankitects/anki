<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { computePosition, autoUpdate } from "@floating-ui/dom";

    export let floating: HTMLElement;

    interface PositionArgs {
        floating: HTMLElement;
    }

    function position(
        reference: HTMLElement,
        args: PositionArgs,
    ): { update(args: PositionArgs): void; destroy(): void } {
        function updateInner(): Promise<void> {
            return computePosition(reference, floating, {
                placement: "bottom-start",
            })
            .then(({ x, y }) => {
                Object.assign(floating.style, {
                    left: `${x}px`,
                    top: `${y}px`,
                });
            });
        }

        let cleanup: () => void;

        function destroy(): void {
            cleanup?.();
        }

        function update({ floating }: PositionArgs): void {
            destroy();

            if (!floating) {
                return;
            }

            cleanup = autoUpdate(reference, floating, updateInner);
        }

        update(args);

        return {
            update,
            destroy,
        };
    }
</script>

<div use:position={{ floating }}>
    <slot />
</div>
