# Brainlift Sync Conflict Rule

Brainlift evidence is derived from Anki's existing card and review-history records. It does not add a sync field, protobuf message, or storage schema.

When the same card changes independently on two devices, normal collection sync keeps the card state with the later modification time. The review histories are handled separately: every distinct review must survive, even when the card states conflict.

Revlog IDs are normally timestamp-based, but two offline devices can still
create distinct reviews with the same ID. The server owns the canonical
payload-to-ID mapping. Because clients download before uploading, a client
moves its distinct pending local review above the maximum local or incoming ID,
keeps the server payload at the shared ID, and uploads the relocated review.
The server uniquifies a distinct upload only when needed. An identical same-ID
payload is a replay and remains a no-op.

The focused sync tests cover:

- ten offline reviews from each side converging to the same exact twenty revlog records;
- independent reviews of a twenty-first card preserving both histories while the later card state wins; and
- two different review payloads sharing one original revlog ID and surviving
  under one exact mapping on replicas with asymmetric unrelated high IDs;
- identical chunk replays remaining idempotent; and
- repeated sync cycles preserving complete entry equality.
