<script lang="typescript">
    import ButtonItem from "./ButtonItem.svelte";
    import InnerButton from "./InnerButton.svelte";

    export let className = "";
    export let icon = "";
    export let onClick: (event: ClickEvent) => void;
</script>

<ButtonItem>
    <InnerButton {className} {onClick}>
        {@html icon}
    </InnerButton>
</ButtonItem>
