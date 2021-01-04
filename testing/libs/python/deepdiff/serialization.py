import json
import pickle
import sys
import io
import logging
import re  # NOQA
import builtins  # NOQA
import datetime  # NOQA
import decimal  # NOQA
import ordered_set  # NOQA
import collections  # NOQA
from copy import deepcopy
from collections.abc import Mapping
from deepdiff.helper import (strings, json_convertor_default, get_type, TEXT_VIEW)
from deepdiff.model import DeltaResult

logger = logging.getLogger(__name__)

try:
    import jsonpickle
except ImportError:  # pragma: no cover. Json pickle is getting deprecated.
    jsonpickle = None  # pragma: no cover. Json pickle is getting deprecated.


MAX_HEADER_LENGTH = 256

MODULE_NOT_FOUND_MSG = 'DeepDiff Delta did not find {} in your modules. Please make sure it is already imported.'
FORBIDDEN_MODULE_MSG = "Module '{}' is forbidden. You need to explicitly pass it by passing a safe_to_import parameter"
DELTA_IGNORE_ORDER_NEEDS_REPETITION_REPORT = 'report_repetition must be set to True when ignore_order is True to create the delta object.'

SAFE_TO_IMPORT = {
    'builtins.range',
    'builtins.complex',
    'builtins.set',
    'builtins.frozenset',
    'builtins.slice',
    'builtins.str',
    'builtins.bytes',
    'builtins.list',
    'builtins.tuple',
    'builtins.int',
    'builtins.float',
    'builtins.dict',
    'builtins.bool',
    'builtins.bin',
    'builtins.None',
    'datetime.datetime',
    'datetime.time',
    'datetime.timedelta',
    'decimal.Decimal',
    'ordered_set.OrderedSet',
    'collections.namedtuple',
    'deepdiff.helper.OrderedDictPlus',
    're.Pattern',
}


class ModuleNotFoundError(ImportError):
    """
    Raised when the module is not found in sys.modules
    """
    pass


class ForbiddenModule(ImportError):
    """
    Raised when a module is not explicitly allowed to be imported
    """
    pass


class SerializationMixin:

    def to_json_pickle(self):
        """
        :ref:`to_json_pickle_label`
        Get the json pickle of the diff object. Unless you need all the attributes and functionality of DeepDiff, running to_json() is the safer option that json pickle.
        """
        if jsonpickle:
            copied = self.copy()
            return jsonpickle.encode(copied)
        else:
            logger.error('jsonpickle library needs to be installed in order to run to_json_pickle')  # pragma: no cover. Json pickle is getting deprecated.

    @classmethod
    def from_json_pickle(cls, value):
        """
        :ref:`from_json_pickle_label`
        Load DeepDiff object with all the bells and whistles from the json pickle dump.
        Note that json pickle dump comes from to_json_pickle
        """
        if jsonpickle:
            return jsonpickle.decode(value)
        else:
            logger.error('jsonpickle library needs to be installed in order to run from_json_pickle')  # pragma: no cover. Json pickle is getting deprecated.

    def to_json(self, default_mapping=None):
        """
        Dump json of the text view.
        **Parameters**

        default_mapping : dictionary(optional), a dictionary of mapping of different types to json types.

        by default DeepDiff converts certain data types. For example Decimals into floats so they can be exported into json.
        If you have a certain object type that the json serializer can not serialize it, please pass the appropriate type
        conversion through this dictionary.

        **Example**

        Serialize custom objects
            >>> class A:
            ...     pass
            ...
            >>> class B:
            ...     pass
            ...
            >>> t1 = A()
            >>> t2 = B()
            >>> ddiff = DeepDiff(t1, t2)
            >>> ddiff.to_json()
            TypeError: We do not know how to convert <__main__.A object at 0x10648> of type <class '__main__.A'> for json serialization. Please pass the default_mapping parameter with proper mapping of the object to a basic python type.

            >>> default_mapping = {A: lambda x: 'obj A', B: lambda x: 'obj B'}
            >>> ddiff.to_json(default_mapping=default_mapping)
            '{"type_changes": {"root": {"old_type": "A", "new_type": "B", "old_value": "obj A", "new_value": "obj B"}}}'
        """
        dic = self.to_dict(view_override=TEXT_VIEW)
        return json.dumps(dic, default=json_convertor_default(default_mapping=default_mapping))

    def to_dict(self, view_override=None):
        """
        convert the result to a python dictionary. You can override the view type by passing view_override.

        **Parameters**

        view_override: view type, default=None,
            override the view that was used to generate the diff when converting to the dictionary.
            The options are the text or tree.
        """

        view = view_override if view_override else self.view
        return dict(self._get_view_results(view))

    def _to_delta_dict(self, directed=True, report_repetition_required=True):
        """
        Dump to a dictionary suitable for delta usage.
        Unlike to_dict, this is not dependent on the original view that the user chose to create the diff.

        **Parameters**

        directed : Boolean, default=True, whether to create a directional delta dictionary or a symmetrical

        Note that in the current implementation the symmetrical delta (non-directional) is ONLY used for verifying that
        the delta is being applied to the exact same values as what was used to generate the delta and has
        no other usages.

        If this option is set as True, then the dictionary will not have the "old_value" in the output.
        Otherwise it will have the "old_value". "old_value" is the value of the item in t1.

        If delta = Delta(DeepDiff(t1, t2)) then
        t1 + delta == t2

        Note that it the items in t1 + delta might have slightly different order of items than t2 if ignore_order
        was set to be True in the diff object.

        """
        result = DeltaResult(tree_results=self.tree, ignore_order=self.ignore_order)
        result.remove_empty_keys()
        if report_repetition_required and self.ignore_order and not self.report_repetition:
            raise ValueError(DELTA_IGNORE_ORDER_NEEDS_REPETITION_REPORT)
        if directed:
            for report_key, report_value in result.items():
                if isinstance(report_value, Mapping):
                    for path, value in report_value.items():
                        if isinstance(value, Mapping) and 'old_value' in value:
                            del value['old_value']
        if self._numpy_paths:
            # Note that keys that start with '_' are considered internal to DeepDiff
            # and will be omitted when counting distance. (Look inside the distance module.)
            result['_numpy_paths'] = self._numpy_paths

        return deepcopy(dict(result))

    def pretty(self):
        """
        The pretty human readable string output for the diff object
        regardless of what view was used to generate the diff.

        Example:
            >>> t1={1,2,4}
            >>> t2={2,3}
            >>> print(DeepDiff(t1, t2).pretty())
            Item root[3] added to set.
            Item root[4] removed from set.
            Item root[1] removed from set.
        """
        result = []
        keys = sorted(self.tree.keys())  # sorting keys to guarantee constant order across python versions.
        for key in keys:
            for item_key in self.tree[key]:
                result += [pretty_print_diff(item_key)]

        return '\n'.join(result)


class _RestrictedUnpickler(pickle.Unpickler):

    def __init__(self, *args, **kwargs):
        self.safe_to_import = kwargs.pop('safe_to_import', None)
        if self.safe_to_import:
            if isinstance(self.safe_to_import, strings):
                self.safe_to_import = set([self.safe_to_import])
            elif isinstance(self.safe_to_import, (set, frozenset)):
                pass
            else:
                self.safe_to_import = set(self.safe_to_import)
            self.safe_to_import = self.safe_to_import | SAFE_TO_IMPORT
        else:
            self.safe_to_import = SAFE_TO_IMPORT
        super().__init__(*args, **kwargs)

    def find_class(self, module, name):
        # Only allow safe classes from self.safe_to_import.
        module_dot_class = '{}.{}'.format(module, name)
        if module_dot_class in self.safe_to_import:
            try:
                module_obj = sys.modules[module]
            except KeyError:
                raise ModuleNotFoundError(MODULE_NOT_FOUND_MSG.format(module_dot_class)) from None
            return getattr(module_obj, name)
        # Forbid everything else.
        raise ForbiddenModule(FORBIDDEN_MODULE_MSG.format(module_dot_class)) from None


def pickle_dump(obj):
    # We expect at least python 3.5 so protocol 4 is good.
    return pickle.dumps(obj, protocol=4, fix_imports=False)


def pickle_load(content, safe_to_import=None):
    """
    **pickle_load**
    Load the pickled content. content should be a bytes object.

    **Parameters**

    content : Bytes of pickled object. It needs to have Delta header in it that is
        separated by a newline character from the rest of the pickled object.

    safe_to_import : A set of modules that needs to be explicitly allowed to be loaded.
        Example: {'mymodule.MyClass', 'decimal.Decimal'}
        Note that this set will be added to the basic set of modules that are already allowed.
        The set of what is already allowed can be found in deepdiff.serialization.SAFE_TO_IMPORT

    **Returns**

        A delta object that can be added to t1 to recreate t2.

    **Examples**

    Importing
        >>> from deepdiff import DeepDiff, Delta
        >>> from pprint import pprint


    """
    if isinstance(content, str):
        content = content.encode('utf-8')
    return _RestrictedUnpickler(io.BytesIO(content), safe_to_import=safe_to_import).load()


PRETTY_FORM_TEXTS = {
    "type_changes": "Type of {diff_path} changed from {type_t1} to {type_t2} and value changed from {val_t1} to {val_t2}.",
    "values_changed": "Value of {diff_path} changed from {val_t1} to {val_t2}.",
    "dictionary_item_added": "Item {diff_path} added to dictionary.",
    "dictionary_item_removed": "Item {diff_path} removed from dictionary.",
    "iterable_item_added": "Item {diff_path} added to iterable.",
    "iterable_item_removed": "Item {diff_path} removed from iterable.",
    "attribute_added": "Attribute {diff_path} added.",
    "attribute_removed": "Attribute {diff_path} removed.",
    "set_item_added": "Item root[{val_t2}] added to set.",
    "set_item_removed": "Item root[{val_t1}] removed from set.",
    "repetition_change": "Repetition change for item {diff_path}.",
}


def pretty_print_diff(diff):
    type_t1 = get_type(diff.t1).__name__
    type_t2 = get_type(diff.t2).__name__

    val_t1 = '"{}"'.format(str(diff.t1)) if type_t1 == "str" else str(diff.t1)
    val_t2 = '"{}"'.format(str(diff.t2)) if type_t2 == "str" else str(diff.t2)

    diff_path = diff.path(root='root')
    return PRETTY_FORM_TEXTS.get(diff.report_type, "").format(
        diff_path=diff_path,
        type_t1=type_t1,
        type_t2=type_t2,
        val_t1=val_t1,
        val_t2=val_t2)
