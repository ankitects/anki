# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from anki.decks import FilteredDeckConfig
from aqt.filtered_deck import FilteredDeckConfigDialog

Order = FilteredDeckConfig.SearchTerm.Order


def available_orders(*, fsrs_enabled: bool) -> list[int]:
    return FilteredDeckConfigDialog._available_order_values(
        order_count=len(Order.keys()), fsrs_enabled=fsrs_enabled
    )


def test_available_orders_follow_fsrs_state() -> None:
    all_orders = available_orders(fsrs_enabled=True)
    assert all_orders == list(range(len(Order.keys())))

    sm2_orders = available_orders(fsrs_enabled=False)
    assert Order.RETRIEVABILITY_ASCENDING not in sm2_orders
    assert Order.RETRIEVABILITY_DESCENDING not in sm2_orders
    assert Order.RELATIVE_OVERDUENESS in sm2_orders


@patch("aqt.filtered_deck.restoreGeom")
@patch("aqt.filtered_deck.disable_help_button")
@patch("aqt.filtered_deck.qconnect")
@patch("aqt.filtered_deck.theme_manager")
@patch("aqt.filtered_deck.tr")
@patch("aqt.filtered_deck.aqt.forms.filtered_deck.Ui_Dialog")
def test_initial_setup_hides_retrievability_orders_without_fsrs(
    ui_dialog: MagicMock,
    _tr: MagicMock,
    _theme_manager: MagicMock,
    _qconnect: MagicMock,
    _disable_help_button: MagicMock,
    _restore_geom: MagicMock,
) -> None:
    labels = list(Order.keys())
    dialog = MagicMock()
    dialog.col.sched.filtered_deck_order_labels.return_value = labels
    dialog.mw.col.get_config.return_value = False
    dialog.FSRS_ONLY_ORDERS = FilteredDeckConfigDialog.FSRS_ONLY_ORDERS
    dialog.GEOMETRY_KEY = FilteredDeckConfigDialog.GEOMETRY_KEY
    dialog._available_order_values.side_effect = (
        FilteredDeckConfigDialog._available_order_values
    )
    form = ui_dialog.return_value

    FilteredDeckConfigDialog._initial_dialog_setup(dialog)

    expected_values = available_orders(fsrs_enabled=False)
    expected_labels = [labels[order] for order in expected_values]
    assert dialog._order_values == expected_values
    form.order.addItems.assert_called_once_with(expected_labels)
    form.order_2.addItems.assert_called_once_with(expected_labels)


def test_combo_row_preserves_available_order_and_falls_back_to_random() -> None:
    dialog = MagicMock()
    dialog._order_values = available_orders(fsrs_enabled=False)
    dialog.DEFAULT_ORDER = FilteredDeckConfigDialog.DEFAULT_ORDER

    relative_overdueness_row = FilteredDeckConfigDialog._combo_row(
        dialog, Order.RELATIVE_OVERDUENESS
    )
    unavailable_order_row = FilteredDeckConfigDialog._combo_row(
        dialog, Order.RETRIEVABILITY_ASCENDING
    )

    assert dialog._order_values[relative_overdueness_row] == Order.RELATIVE_OVERDUENESS
    assert dialog._order_values[unavailable_order_row] == Order.RANDOM


@patch("aqt.filtered_deck.without_unicode_isolation", return_value="title")
@patch("aqt.filtered_deck.tr")
def test_load_deck_maps_saved_orders_to_combo_rows(
    _tr: MagicMock, _without_unicode_isolation: MagicMock
) -> None:
    dialog = MagicMock()
    dialog.form = MagicMock()
    dialog._order_values = available_orders(fsrs_enabled=False)
    dialog.DEFAULT_ORDER = FilteredDeckConfigDialog.DEFAULT_ORDER
    dialog._combo_row.side_effect = lambda order: FilteredDeckConfigDialog._combo_row(
        dialog, order
    )
    dialog.deck = SimpleNamespace(
        id=1,
        name="Filtered",
        config=FilteredDeckConfig(
            reschedule=True,
            search_terms=[
                FilteredDeckConfig.SearchTerm(
                    search="deck:Default",
                    limit=100,
                    order=Order.RETRIEVABILITY_ASCENDING,
                ),
                FilteredDeckConfig.SearchTerm(
                    search="is:due",
                    limit=20,
                    order=Order.RELATIVE_OVERDUENESS,
                ),
            ],
        ),
    )

    FilteredDeckConfigDialog._load_deck(dialog)

    first_row = dialog.form.order.setCurrentIndex.call_args.args[0]
    second_row = dialog.form.order_2.setCurrentIndex.call_args.args[0]
    assert dialog._order_values[first_row] == Order.RANDOM
    assert dialog._order_values[second_row] == Order.RELATIVE_OVERDUENESS

    dialog.deck.config.search_terms.pop()
    dialog.form.order_2.setCurrentIndex.reset_mock()
    FilteredDeckConfigDialog._load_deck(dialog)

    default_second_row = dialog.form.order_2.setCurrentIndex.call_args.args[0]
    assert dialog._order_values[default_second_row] == Order.RANDOM


def test_update_deck_maps_combo_rows_back_to_orders() -> None:
    dialog = MagicMock()
    dialog._order_values = available_orders(fsrs_enabled=False)
    dialog.form = MagicMock()
    dialog.deck = SimpleNamespace(
        name="Filtered",
        config=FilteredDeckConfig(),
    )

    relative_overdueness_row = dialog._order_values.index(Order.RELATIVE_OVERDUENESS)
    random_row = dialog._order_values.index(Order.RANDOM)
    dialog.form.order.currentIndex.return_value = relative_overdueness_row
    dialog.form.order_2.currentIndex.return_value = random_row
    dialog.form.secondFilter.isChecked.return_value = True
    dialog.form.search.text.return_value = "deck:Default"
    dialog.form.limit.value.return_value = 100
    dialog.form.search_2.text.return_value = "is:due"
    dialog.form.limit_2.value.return_value = 20
    dialog.form.resched.isChecked.return_value = True
    dialog.form.preview_again.value.return_value = 60
    dialog.form.preview_hard.value.return_value = 600
    dialog.form.preview_good.value.return_value = 0

    assert FilteredDeckConfigDialog._update_deck(dialog)

    terms = dialog.deck.config.search_terms
    assert terms[0].order == Order.RELATIVE_OVERDUENESS
    assert terms[1].order == Order.RANDOM
