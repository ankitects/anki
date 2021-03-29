<script lang="typescript">
    import InnerButton from "./InnerButton.svelte";

    export let className = "";
    export let icon = "";
    export let onClick: (event: ClickEvent) => void;
</script>

<InnerButton {className} {onClick}>
    {@html icon}
</InnerButton>
