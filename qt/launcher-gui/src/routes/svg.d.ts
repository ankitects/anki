// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// TODO: this is purely to make svelte-check happy, as it complains about the icon components not being found otherwise

declare module "*.svg?component" {
    import type { Component } from "svelte";
    import type { SVGAttributes } from "svelte/elements";

    const content: Component<SVGAttributes<SVGSVGElement>>;

    export default content;
}
