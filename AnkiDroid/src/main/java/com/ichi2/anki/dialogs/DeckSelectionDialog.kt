/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.getDrawableOrThrow
import androidx.core.content.res.use
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import anki.decks.deckTreeNode
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.DialogDeckPickerBinding
import com.ichi2.anki.databinding.ItemDeckPickerDialogBinding
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.ARG_SELECTED_DECK
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.REQUEST_SELECT_DECK
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.TypedFilter
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber

/**
 * A fragment which allows the user to select one or multiple decks from a list of decks. It also
 * provides options to filter the list of decks and add top level or sub decks.
 *
 * @see SelectableDeck
 */
@NeedsTest("simulate 'don't keep activities'")
@NeedsTest("Test the ordering of the dialog")
@NeedsTest("test the ordering of decks in search page in the dialog")
@NeedsTest("test syncing the status of collapsing deck with teh deckPicker")
class DeckSelectionDialog : AnalyticsDialogFragment() {
    private lateinit var binding: DialogDeckPickerBinding
    private lateinit var decksAdapter: DecksArrayAdapter
    private lateinit var decksRoot: DeckNode
    private val title: String
        get() =
            requireArguments().getString(ARG_TITLE, null)
                ?: getString(R.string.select_deck_title)
    private val templateEditorMessage: String?
        get() = requireArguments().getString(ARG_TEMPLATE_EDITOR_MESSAGE, null)
    private val allowMultipleSelection: Boolean
        get() = requireArguments().getBoolean(ARG_ALLOW_MULTIPLE_SELECTION, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = true
        binding = DialogDeckPickerBinding.inflate(LayoutInflater.from(context))
        binding.templateEditorMessage.isVisible = templateEditorMessage != null
        binding.templateEditorMessage.text = templateEditorMessage
        binding.decks.requestFocus()
        val dividerItemDecoration =
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        binding.decks.addItemDecoration(dividerItemDecoration)
        val decks: List<SelectableDeck> = getDeckNames()
        decksAdapter = DecksArrayAdapter(decks)
        binding.decks.adapter = decksAdapter
        setupMenu()
        return AlertDialog.Builder(requireActivity()).create {
            negativeButton(R.string.dialog_cancel)
            customView(view = binding.root)
            if (templateEditorMessage != null) {
                positiveButton(R.string.restore_default) {
                    onDeckSelected(null)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // needed otherwise the keyboard is not shown for the toolbar SearchView input
        dialog?.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        )
    }

    private fun getDeckNames(): ArrayList<SelectableDeck> =
        BundleCompat.getParcelableArrayList(requireArguments(), DECK_NAMES, SelectableDeck::class.java)!!

    private fun setupMenu() {
        val toolbar: Toolbar = binding.toolbar
        toolbar.title = title
        toolbar.inflateMenu(R.menu.deck_picker_dialog_menu)
        val searchItem = toolbar.menu.findItem(R.id.action_filter)
        val searchView = searchItem.actionView as AccessibleSearchView
        searchView.queryHint = getString(R.string.deck_picker_dialog_filter_decks)
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    searchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    decksAdapter.filter.filter(newText)
                    return true
                }
            },
        )
        val addDecks = toolbar.menu.findItem(R.id.action_add_deck)
        addDecks?.setOnMenuItemClickListener {
            // creating new deck without any parent deck
            showDeckDialog()
            true
        }
    }

    /**
     * Displays a dialog to create a subdeck under the specified parent deck.
     *
     * @param parentDeck The parent deck under which the subdeck will be created
     */
    private fun showSubDeckDialog(parentDeck: SelectableDeck.Deck) {
        val createDeckDialog =
            CreateDeckDialog(requireActivity(), R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, parentDeck.deckId)
        createDeckDialog.onNewDeckCreated = { did: DeckId -> onNewDeckCreated(did) }
        createDeckDialog.showDialog()
    }

    private fun showDeckDialog() {
        val createDeckDialog = CreateDeckDialog(requireActivity(), R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
        createDeckDialog.onNewDeckCreated = { did: DeckId -> onNewDeckCreated(did) }
        createDeckDialog.showDialog()
    }

    /** Updates the list and simulates a click on the newly created deck */
    private fun onNewDeckCreated(id: DeckId) {
        // a deck/subdeck was created
        launchCatchingTask {
            val name = withCol { decks.name(id) }
            val deck = SelectableDeck.Deck(id, name)
            selectDeckAndClose(deck)
        }
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY) ?: REQUEST_SELECT_DECK
        parentFragmentManager.setFragmentResult(
            requestKey,
            Bundle().apply {
                putParcelable(ARG_SELECTED_DECK, deck)
            },
        )
    }

    /**
     * Same action as pressing on the deck in the list. I.e. send the deck to listener and close the
     * dialog. When multiple selection is enabled, the dialog stays open and removes the selected
     * deck from the list.
     */
    private fun selectDeckAndClose(deck: SelectableDeck) {
        Timber.d("selected deck '%s'", deck)
        onDeckSelected(deck)
        if (allowMultipleSelection) {
            if (deck is SelectableDeck.Deck) {
                decksAdapter.removeDeck(deck.deckId)
            }
            // dismiss dialog when all decks have been selected
            if (decksAdapter.itemCount == 0) {
                dialog!!.dismiss()
            }
        } else {
            dialog!!.dismiss()
        }
    }

    open inner class DecksArrayAdapter(
        decks: List<SelectableDeck>,
    ) : RecyclerView.Adapter<DecksArrayAdapter.ViewHolder>(),
        Filterable {
        private lateinit var expandImage: Drawable
        private lateinit var collapseImage: Drawable

        val attrs =
            intArrayOf(
                R.attr.expandRef,
                R.attr.collapseRef,
            )

        inner class ViewHolder(
            private val binding: ItemDeckPickerDialogBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            private var currentDeck: SelectableDeck? = null

            val expander: ImageButton = binding.expander
            val indentView: ImageButton = binding.indent

            fun setDeck(deck: SelectableDeck) {
                binding.deckTextView.text = deck.getDisplayName(requireContext())
                currentDeck = deck
            }

            init {
                binding.root.setOnClickListener {
                    currentDeck?.let { selectDeckAndClose(it) }
                }
                expander.setOnClickListener {
                    currentDeck?.let { toggleExpansion(it) }
                }
                binding.root.setOnContextAndLongClickListener {
                    // creating sub deck with parent deck path
                    currentDeck?.let { deck ->
                        if (deck is SelectableDeck.Deck) {
                            showSubDeckDialog(deck)
                        }
                    }

                    true
                }
            }

            private fun toggleExpansion(deck: SelectableDeck) {
                val deckId =
                    when (deck) {
                        is SelectableDeck.AllDecks -> return
                        is SelectableDeck.Deck -> deck.deckId
                    }
                decksRoot.find(deckId)?.apply {
                    collapsed = !collapsed
                    Timber.d("The deck with ID $id is currently expanded: ${!collapsed}.")
                    updateCurrentlyDisplayedDecks()
                }
            }
        }

        private fun updateCurrentlyDisplayedDecks() {
            currentlyDisplayedDecks.clear()
            currentlyDisplayedDecks.addAll(allDecksList.filter(::isViewable))
            notifyDataSetChanged()
        }

        fun removeDeck(deckId: DeckId) {
            val idsToRemove = mutableSetOf(deckId)
            decksRoot.find(deckId)?.forEach { idsToRemove.add(it.did) }
            allDecksList.removeAll { it.did in idsToRemove }
            updateCurrentlyDisplayedDecks()
        }

        private val allDecksList = ArrayList<DeckNode>()
        private val currentlyDisplayedDecks = ArrayList<DeckNode>()

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val layoutInflater = LayoutInflater.from(context)
            val binding = ItemDeckPickerDialogBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val deck = currentlyDisplayedDecks[position]
            val isDeckViewable = isViewable(deck)
            holder.itemView.isVisible = isDeckViewable
            if (isDeckViewable) {
                val model = if (deck.did == ALL_DECKS_ID) SelectableDeck.AllDecks else SelectableDeck.Deck(deck.did, deck.fullDeckName)
                holder.setDeck(model)
            }
            setDeckExpander(holder.expander, holder.indentView, deck)
        }

        /**
         * Sets the expander and indent views based on the properties of the provided DeckNode.
         *
         * @param expander The ImageButton used for expanding/collapsing the deck node.
         * @param indent The ImageButton used for indenting the deck node.
         * @param node The DeckNode representing the deck.
         */
        private fun setDeckExpander(
            expander: ImageButton,
            indent: ImageButton,
            node: DeckNode,
        ) {
            if (hasSubDecks(node)) {
                expander.apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    setImageDrawable(if (node.collapsed) expandImage else collapseImage)
                    contentDescription = context.getString(if (node.collapsed) R.string.expand else R.string.collapse)
                    visibility = View.VISIBLE
                }
            } else {
                expander.apply {
                    visibility = View.INVISIBLE
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }
            indent.minimumWidth = node.depth * expander.resources.getDimensionPixelSize(R.dimen.keyline_1)
        }

        private fun hasSubDecks(node: DeckNode): Boolean = node.children.isNotEmpty()

        private fun isViewable(deck: DeckNode): Boolean {
            val parentNodeRef = deck.parent ?: return true
            // The parent belongs to the tree retained by [allDecksList], so should still exist.
            val parentNode = parentNodeRef.get()!!
            return !parentNode.collapsed && isViewable(parentNode)
        }

        override fun getItemCount(): Int = currentlyDisplayedDecks.size

        override fun getFilter(): Filter = DecksFilter()

        private inner class DecksFilter : TypedFilter<DeckNode>(allDecksList) {
            /**
             * Returns all the deck nodes of [items] that contains every pattern of the constraints.
             * In the constraints, patterns are separated by any whitespace character.
             */
            override fun filterResults(
                constraint: CharSequence,
                items: List<DeckNode>,
            ) = DeckFilters.create(constraint).let { deckFilters ->
                items.filter { node ->
                    deckFilters.accept(node.fullDeckName)
                }
            }

            override fun publishResults(
                constraint: CharSequence?,
                results: List<DeckNode>,
            ) {
                results.forEach { it.collapsed = false }
                currentlyDisplayedDecks.apply {
                    clear()
                    addAll(results)
                }
                notifyDataSetChanged()
            }
        }

        init {
            requireContext().obtainStyledAttributes(attrs).use { typedArray ->
                expandImage = typedArray.getDrawableOrThrow(0)
                expandImage.isAutoMirrored = true
                collapseImage = typedArray.getDrawableOrThrow(1)
                collapseImage.isAutoMirrored = true
            }

            launchCatchingTask {
                decksRoot = withCol { Pair(sched.deckDueTree(), isEmpty) }.first
                val allDecksSet =
                    decks
                        .mapNotNull { it as? SelectableDeck.Deck }
                        .mapNotNull { decksRoot.find(it.deckId) }
                        .toSet()
                if (decks.any { it is SelectableDeck.AllDecks }) {
                    val newDeckNode =
                        deckTreeNode {
                            deckId = ALL_DECKS_ID
                            name = "all"
                        }
                    allDecksList.add(DeckNode(newDeckNode, getString(R.string.card_browser_all_decks), null))
                }

                allDecksList.addAll(allDecksSet)
                updateCurrentlyDisplayedDecks()
            }
        }
    }

    // TODO: allow filtering to SelectableDeck.Deck, excluding 'AllDecks'

    companion object {
        const val TAG = "DeckSelectionDialog"
        const val REQUEST_SELECT_DECK = "request_select_deck"
        const val ARG_SELECTED_DECK = "arg_selected_deck"
        const val ARG_REQUEST_KEY = "arg_request_key"
        const val ARG_ALLOW_ALL = "arg_allow_all"
        const val ARG_ALLOW_FILTERED = "arg_allow_filtered"
        const val ARG_SKIP_EMPTY_DEFAULT = "arg_skip_empty_default"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_TEMPLATE_EDITOR_MESSAGE = "arg_template_editor_message"
        private const val DECK_NAMES = "deckNames"
        private const val ARG_ALLOW_MULTIPLE_SELECTION = "arg_allow_multiple_selection"

        /** Creates a new instance of [DeckSelectionDialog]. */
        fun newInstance(
            title: String? = null,
            templateEditorMessage: String? = null,
            decks: List<SelectableDeck>,
            requestKey: String = REQUEST_SELECT_DECK,
            allowMultipleSelection: Boolean = false,
            allowAll: Boolean = true,
            allowFiltered: Boolean = true,
            skipEmptyDefault: Boolean = false,
        ): DeckSelectionDialog =
            DeckSelectionDialog().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_TEMPLATE_EDITOR_MESSAGE, templateEditorMessage)
                        putString(ARG_TITLE, title)
                        putParcelableArrayList(DECK_NAMES, ArrayList(decks))
                        putString(ARG_REQUEST_KEY, requestKey)
                        putBoolean(ARG_ALLOW_MULTIPLE_SELECTION, allowMultipleSelection)
                        putBoolean(ARG_ALLOW_ALL, allowAll)
                        putBoolean(ARG_ALLOW_FILTERED, allowFiltered)
                        putBoolean(ARG_SKIP_EMPTY_DEFAULT, skipEmptyDefault)
                    }
            }
    }
}

/**
 * Register a fragment result listener to listen for a deck selection.
 * @param requestKey usually [REQUEST_SELECT_DECK], but can be changed to handle situations when
 * there are multiple listeners for this event that do different things in response
 * @param action a lambda that provides the user selected deck
 */
fun FragmentActivity.registerDeckSelectedHandler(
    requestKey: String = REQUEST_SELECT_DECK,
    action: (deck: SelectableDeck?) -> Unit,
) {
    setFragmentResultListener(requestKey) { _, bundle ->
        val selectedDeck = bundle.getParcelableCompat<SelectableDeck?>(ARG_SELECTED_DECK)
        action(selectedDeck)
    }
}

/**
 * Register a fragment result listener to listen for a deck selection.
 * Note: the fragment result listener is set on [Fragment.getParentFragmentManager]
 * @param requestKey usually [REQUEST_SELECT_DECK], but can be changed to handle situations when
 * there are multiple listeners for this event that do different things in response
 * @param action a lambda that provides the user selected deck
 */
fun Fragment.registerDeckSelectedHandler(
    requestKey: String = REQUEST_SELECT_DECK,
    action: (deck: SelectableDeck?) -> Unit,
) {
    setFragmentResultListener(requestKey) { _, bundle ->
        val selectedDeck = bundle.getParcelableCompat<SelectableDeck?>(ARG_SELECTED_DECK)
        action(selectedDeck)
    }
}
