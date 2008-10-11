# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

def importAll():
    # a hack
    import main
    import about
    import unsaved
    import help
    import preferences
    import addcards
    import lookup
    import sync
    import view
    import cardlist
    import update
    import importing
    import exporting
    import status
    import deckproperties
    import modelproperties
    import modelchooser
    import displayproperties
    import utils
    import facteditor
    import tagedit
    import tray
    import activetags

class DialogManager(object):

    def __init__(self):
        self.modelessDialogs = {}

    def registerDialog(self, name, klass):
        self.modelessDialogs[name] = (klass, None)

    def open(self, name, obj):
        self.modelessDialogs[name] = (
            self.modelessDialogs[name][0], obj)

    def close(self, name):
        self.modelessDialogs[name] = (
            self.modelessDialogs[name][0], None)

    def get(self, name, *args):
        (klass, obj) = self.modelessDialogs[name]
        if obj:
            return obj
        else:
            return klass(*args)

    def closeAll(self):
        for (n, (klass, obj)) in self.modelessDialogs.items():
            if obj:
                obj.hide()
                self.close(n)

    # since we load the graphs dynamically, we need a proxy for this
    def graphProxy(self, *args):
        import graphs
        return graphs.intervalGraph(*args)

    def registerDialogs(self):
        self.registerDialog("AddCards", addcards.AddCards)
        self.registerDialog("CardList", cardlist.EditDeck)
        self.registerDialog("DisplayProperties", displayproperties.DisplayProperties)
        self.registerDialog("Graphs", self.graphProxy)

dialogs = DialogManager()
