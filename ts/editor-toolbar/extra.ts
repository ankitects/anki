import IconButton from "./IconButton.svelte";
import bracketsIcon from "./code-brackets.svg";
import paperclipIcon from "./paperclip.svg";
import micIcon from "./mic.svg";
import threeDotsIcon from "./three-dots.svg";

export const clozeButton = { component: IconButton, icon: bracketsIcon };
export const attachmentButton = { component: IconButton, icon: paperclipIcon };
export const micButton = { component: IconButton, icon: micIcon };
export const etcButton = { component: IconButton, icon: threeDotsIcon };
