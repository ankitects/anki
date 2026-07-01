# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from tests.shared import getEmptyCol


def _add(col, front, tags):
    note = col.newNote()
    note["Front"] = front
    note.tags = tags
    col.addNote(note)
    return note


def test_never_learned_round_trip():
    """e2e smoke test through the exact codegen'd snake_case surface:
    mark a topic via set_never_learned, see it show up in
    never_learned_list grouped + labeled correctly, then unmark and see
    it disappear."""
    col = getEmptyCol()
    a = _add(col, "front a", ["bio::cell"])
    b = _add(col, "front b", ["bio::cell::wall"])
    _add(col, "front c", [])  # no topic tag, not part of this topic

    topic_card_ids = set(col.card_ids_of_note(a.id)) | set(
        col.card_ids_of_note(b.id)
    )
    assert len(topic_card_ids) == 2
    card_id = next(iter(col.card_ids_of_note(a.id)))

    # Mark the whole topic as never-learned via a card in it.
    resp = col._backend.set_never_learned(
        card_id=card_id, group_depth=2, enabled=True
    )
    # OpChangesWithCount-shaped: has .count and .changes.
    assert resp.count == len(topic_card_ids)
    assert resp.changes

    # Read it back via never_learned_list.
    listing = col._backend.never_learned_list(group_depth=2, search="")
    assert isinstance(list(listing.groups), list)
    assert isinstance(listing.total_cards, int)
    assert listing.total_cards == len(topic_card_ids)

    groups_by_tag = {g.tag: g for g in listing.groups}
    assert "bio::cell" in groups_by_tag
    bio_group = groups_by_tag["bio::cell"]
    listed_ids = {c.card_id for c in bio_group.cards}
    assert listed_ids == topic_card_ids

    labels = {c.card_id: c.label for c in bio_group.cards}
    for cid in col.card_ids_of_note(a.id):
        assert labels[cid] == "front a"
    for cid in col.card_ids_of_note(b.id):
        assert labels[cid] == "front b"

    # Unmark the topic; it should disappear from a subsequent listing.
    unmark_resp = col._backend.set_never_learned(
        card_id=card_id, group_depth=2, enabled=False
    )
    assert unmark_resp.count == len(topic_card_ids)

    listing_after = col._backend.never_learned_list(group_depth=2, search="")
    groups_after = {g.tag: g for g in listing_after.groups}
    assert "bio::cell" not in groups_after

    # Sanity: nothing about these mutations corrupted the collection.
    (_, ok) = col.fix_integrity()
    assert ok is True


def test_never_learned_list_empty_is_honest_empty_state():
    """AC3: no NeverLearned-tagged notes -> {groups: [], total_cards: 0},
    never an error."""
    col = getEmptyCol()
    _add(col, "a", ["bio::cell"])
    _add(col, "b", [])

    listing = col._backend.never_learned_list(group_depth=2, search="")
    assert list(listing.groups) == []
    assert listing.total_cards == 0
