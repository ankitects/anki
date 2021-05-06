<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Detachable from "components/Detachable.svelte";

    import type { ButtonGroupRegistration } from "./buttons";
    import type { Register } from "./registration";

    import { getContext, hasContext } from "svelte";
    import { buttonToolbarKey } from "./contextKeys";

    export let id: string | undefined = undefined;
    export let registration: ButtonGroupRegistration | undefined = undefined;

    let detach_: boolean;

    if (registration) {
        const { detach } = registration;
        detach.subscribe((value: boolean) => (detach_ = value));
    } else if (hasContext(buttonToolbarKey)) {
        const registerComponent = getContext<Register<ButtonGroupRegistration>>(
            buttonToolbarKey
        );
        const { detach } = registerComponent();
        detach.subscribe((value: boolean) => (detach_ = value));
    } else {
        detach_ = false;
    }
</script>

<style lang="scss">
    div {
        display: contents;
    }
</style>

<!-- div is necessary to preserve item position -->
<div {id}>
    <Detachable detach={detach_}>
        <slot />
    </Detachable>
</div>
