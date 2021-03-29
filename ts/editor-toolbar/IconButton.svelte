<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";
    import IconButtonInner from "./IconButtonInner.svelte";

    export let className = "";
    export let icon = "";
    export let onClick: (event: ClickEvent) => void;
</script>

<ButtonItem>
    <IconButtonInner {className} active={false} {onClick}>
        {@html icon}
    </IconButtonInner>
</ButtonItem>
