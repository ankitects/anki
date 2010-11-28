# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

def importAll():
    # a hack
    import main
    import about
    import activetags
    import addcards
    import cardlist
    import deckproperties
    import importing
    import clayout
    import exporting
    import facteditor
    import help
    import modelchooser
    import modelproperties
    import preferences
    import status
    import sync
    import tagedit
    import tray
    import unsaved
    import update
    import utils
    import view
    import getshared

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
            obj.activateWindow()
            obj.raise_()
            return obj
        else:
            return klass(*args)

    def closeAll(self):
        for (n, (klass, obj)) in self.modelessDialogs.items():
            if obj:
                obj.forceClose = True
                obj.close()
                self.close(n)

    # since we load the graphs dynamically, we need a proxy for this
    def graphProxy(self, *args):
        import graphs
        return graphs.intervalGraph(*args)

    def registerDialogs(self):
        self.registerDialog("AddCards", addcards.AddCards)
        self.registerDialog("CardList", cardlist.EditDeck)
        self.registerDialog("Graphs", self.graphProxy)

dialogs = DialogManager()
