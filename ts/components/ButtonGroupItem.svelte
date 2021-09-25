<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Detachable from "./Detachable.svelte";

    import type { ButtonRegistration } from "./buttons";
    import { ButtonPosition } from "./buttons";
    import type { Register } from "./registration";

    import { getContext, hasContext } from "svelte";
    import { buttonGroupKey } from "./context-keys";

    export let id: string | undefined = undefined;
    export let registration: ButtonRegistration | undefined = undefined;

    let detached: boolean;
    let position_: ButtonPosition;
    let style: string;

    const radius = "5px";

    const leftStyle = `--border-left-radius: ${radius}; --border-right-radius: 0; `;
    const rightStyle = `--border-left-radius: 0; --border-right-radius: ${radius}; `;

    $: {
        switch (position_) {
            case ButtonPosition.Standalone:
                style = `--border-left-radius: ${radius}; --border-right-radius: ${radius}; `;
                break;
            case ButtonPosition.InlineStart:
                style = leftStyle;
                break;
            case ButtonPosition.Center:
                style = "--border-left-radius: 0; --border-right-radius: 0; ";
                break;
            case ButtonPosition.InlineEnd:
                style = rightStyle;
                break;
        }
    }

    if (registration) {
        const { detach, position } = registration;
        detach.subscribe((value: boolean) => (detached = value));
        position.subscribe((value: ButtonPosition) => (position_ = value));
    } else if (hasContext(buttonGroupKey)) {
        const registerComponent =
            getContext<Register<ButtonRegistration>>(buttonGroupKey);
        const { detach, position } = registerComponent();
        detach.subscribe((value: boolean) => (detached = value));
        position.subscribe((value: ButtonPosition) => (position_ = value));
    } else {
        detached = false;
        position_ = ButtonPosition.Standalone;
    }
</script>

<!-- div is necessary to preserve item position -->
<div {id} {style}>
    <Detachable {detached}>
        <slot />
    </Detachable>
</div>
