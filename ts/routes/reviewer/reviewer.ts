import { writable } from "svelte/store"

export function setupReviewer() {
    const html = writable("")

    return {html}
}