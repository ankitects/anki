# Brainlift Sync Conflict Rule

Brainlift evidence is derived from Anki's existing card and review-history records. It does not add a sync field, protobuf message, or storage schema.

When the same card changes independently on two devices, normal collection sync keeps the card state with the later modification time. The review histories are handled separately: every distinct review must survive, even when the card states conflict.

Revlog IDs are normally timestamp-based, but two offline devices can still create distinct reviews with the same ID. During remote merge, Anki uses the existing revlog uniquification path. The local review keeps its ID, and an incoming colliding review is assigned the next available ID instead of being ignored. Repeated syncs then transfer each distinct history once without adding another copy.

The focused sync tests cover:

- ten offline reviews from each side converging to the same exact twenty revlog records;
- independent reviews of a twenty-first card preserving both histories while the later card state wins; and
- two different review payloads sharing one original revlog ID and surviving under unique IDs.
