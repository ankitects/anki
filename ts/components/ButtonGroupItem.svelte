<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import WithTheming from "components/WithTheming.svelte";
    import Detachable from "components/Detachable.svelte";

    import type { ButtonRegistration } from "./buttons";
    import { ButtonPosition } from "./buttons";

    import { getContext, hasContext } from "svelte";
    import { buttonGroupKey } from "./contextKeys";

    export let registration: ButtonRegistration | undefined = undefined;

    let detach_: boolean;
    let position_: ButtonPosition;
    let style: string;

    const radius = "calc(var(--toolbar-size) / 7.5)";

    $: {
        switch (position_) {
            case ButtonPosition.Standalone:
                style = `--border-left-radius: ${radius}; --border-right-radius: ${radius}; `;
                break;
            case ButtonPosition.Leftmost:
                style = `--border-left-radius: ${radius}; --border-right-radius: 0; `;
                break;
            case ButtonPosition.Center:
                style = "--border-left-radius: 0; --border-right-radius: 0; ";
                break;
            case ButtonPosition.Rightmost:
                style = `--border-left-radius: 0; --border-right-radius: ${radius}; `;
                break;
        }
    }

    if (registration) {
        const { detach, position } = registration;
        detach.subscribe((value: boolean) => (detach_ = value));
        position.subscribe((value: ButtonPosition) => (position_ = value));
    } else if (hasContext(buttonGroupKey)) {
        const { registerButton } = getContext(buttonGroupKey);
        const { detach, position } = registerButton();
        detach.subscribe((value: boolean) => (detach_ = value));
        position.subscribe((value: ButtonPosition) => (position_ = value));
    } else {
        detach_ = false;
        position_ = ButtonPosition.Standalone;
    }
</script>

<!-- div in WithTheming is necessary to preserve item position -->
<WithTheming {style}>
    <Detachable detach={detach_}>
        <slot />
    </Detachable>
</WithTheming>
