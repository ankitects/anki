import logging
from collections.abc import Mapping
from copy import deepcopy
from deepdiff import DeepDiff
from deepdiff.serialization import pickle_load, pickle_dump
from deepdiff.helper import (
    strings, short_repr, numbers,
    np_ndarray, np_array_factory, numpy_dtypes, get_doc,
    not_found, numpy_dtype_string_to_type, dict_)
from deepdiff.path import _path_to_elements, _get_nested_obj, GET, GETATTR
from deepdiff.anyset import AnySet


logger = logging.getLogger(__name__)


VERIFICATION_MSG = 'Expected the old value for {} to be {} but it is {}. Error found on: {}'
ELEM_NOT_FOUND_TO_ADD_MSG = 'Key or index of {} is not found for {} for setting operation.'
TYPE_CHANGE_FAIL_MSG = 'Unable to do the type change for {} from to type {} due to {}'
VERIFY_SYMMETRY_MSG = ('while checking the symmetry of the delta. You have applied the delta to an object that has '
                       'different values than the original object the delta was made from')
FAIL_TO_REMOVE_ITEM_IGNORE_ORDER_MSG = 'Failed to remove index[{}] on {}. It was expected to be {} but got {}'
DELTA_NUMPY_OPERATOR_OVERRIDE_MSG = (
    'A numpy ndarray is most likely being added to a delta. '
    'Due to Numpy override the + operator, you can only do: delta + ndarray '
    'and NOT ndarray + delta')
BINIARY_MODE_NEEDED_MSG = "Please open the file in the binary mode and pass to Delta by passing 'b' in open(..., 'b'): {}"
DELTA_AT_LEAST_ONE_ARG_NEEDED = 'At least one of the diff, delta_path or delta_file arguments need to be passed.'
INVALID_ACTION_WHEN_CALLING_GET_ELEM = 'invalid action of {} when calling _get_elem_and_compare_to_old_value'
INVALID_ACTION_WHEN_CALLING_SIMPLE_SET_ELEM = 'invalid action of {} when calling _simple_set_elem_value'
INVALID_ACTION_WHEN_CALLING_SIMPLE_DELETE_ELEM = 'invalid action of {} when calling _simple_set_elem_value'
UNABLE_TO_GET_ITEM_MSG = 'Unable to get the item at {}: {}'
UNABLE_TO_GET_PATH_MSG = 'Unable to get the item at {}'
INDEXES_NOT_FOUND_WHEN_IGNORE_ORDER = 'Delta added to an incompatible object. Unable to add the following items at the specific indexes. {}'
NUMPY_TO_LIST = 'NUMPY_TO_LIST'
NOT_VALID_NUMPY_TYPE = "{} is not a valid numpy type."

doc = get_doc('delta.rst')


class DeltaError(ValueError):
    """
    Delta specific errors
    """
    pass


class DeltaNumpyOperatorOverrideError(ValueError):
    """
    Delta Numpy Operator Override Error
    """
    pass


class Delta:

    __doc__ = doc

    def __init__(
        self,
        diff=None,
        delta_path=None,
        delta_file=None,
        deserializer=pickle_load,
        log_errors=True,
        mutate=False,
        raise_errors=False,
        safe_to_import=None,
        serializer=pickle_dump,
        verify_symmetry=False,
    ):

        if diff is not None:
            if isinstance(diff, DeepDiff):
                self.diff = diff._to_delta_dict(directed=not verify_symmetry)
            elif isinstance(diff, Mapping):
                self.diff = diff
            elif isinstance(diff, strings):
                self.diff = deserializer(diff, safe_to_import=safe_to_import)
        elif delta_path:
            with open(delta_path, 'rb') as the_file:
                content = the_file.read()
            self.diff = deserializer(content, safe_to_import=safe_to_import)
        elif delta_file:
            try:
                content = delta_file.read()
            except UnicodeDecodeError as e:
                raise ValueError(BINIARY_MODE_NEEDED_MSG.format(e)) from None
            self.diff = deserializer(content, safe_to_import=safe_to_import)
        else:
            raise ValueError(DELTA_AT_LEAST_ONE_ARG_NEEDED)

        self.mutate = mutate
        self.verify_symmetry = verify_symmetry
        self.raise_errors = raise_errors
        self.log_errors = log_errors
        self._numpy_paths = self.diff.pop('_numpy_paths', False)
        self.serializer = serializer
        self.deserializer = deserializer
        self.reset()

    def __repr__(self):
        return "<Delta: {}>".format(short_repr(self.diff, max_length=100))

    def reset(self):
        self.post_process_paths_to_convert = dict_()

    def __add__(self, other):
        if isinstance(other, numbers) and self._numpy_paths:
            raise DeltaNumpyOperatorOverrideError(DELTA_NUMPY_OPERATOR_OVERRIDE_MSG)
        if self.mutate:
            self.root = other
        else:
            self.root = deepcopy(other)
        self._do_pre_process()
        self._do_values_changed()
        self._do_set_item_added()
        self._do_set_item_removed()
        self._do_type_changes()
        # NOTE: the remove iterable action needs to happen BEFORE
        # all the other iterables to match the reverse of order of operations in DeepDiff
        self._do_iterable_item_removed()
        self._do_iterable_item_added()
        self._do_ignore_order()
        self._do_dictionary_item_added()
        self._do_dictionary_item_removed()
        self._do_attribute_added()
        self._do_attribute_removed()
        self._do_post_process()

        other = self.root
        # removing the reference to other
        del self.root
        self.reset()
        return other

    __radd__ = __add__

    def _raise_or_log(self, msg, level='error'):
        if self.log_errors:
            getattr(logger, level)(msg)
        if self.raise_errors:
            raise DeltaError(msg)

    def _do_verify_changes(self, path, expected_old_value, current_old_value):
        if self.verify_symmetry and expected_old_value != current_old_value:
            self._raise_or_log(VERIFICATION_MSG.format(
                path, expected_old_value, current_old_value, VERIFY_SYMMETRY_MSG))

    def _get_elem_and_compare_to_old_value(self, obj, path_for_err_reporting, expected_old_value, elem=None, action=None):
        try:
            if action == GET:
                current_old_value = obj[elem]
            elif action == GETATTR:
                current_old_value = getattr(obj, elem)
            else:
                raise DeltaError(INVALID_ACTION_WHEN_CALLING_GET_ELEM.format(action))
        except (KeyError, IndexError, AttributeError, IndexError, TypeError) as e:
            current_old_value = not_found
            if isinstance(path_for_err_reporting, (list, tuple)):
                path_for_err_reporting = '.'.join([i[0] for i in path_for_err_reporting])
            if self.verify_symmetry:
                self._raise_or_log(VERIFICATION_MSG.format(
                    path_for_err_reporting,
                    expected_old_value, current_old_value, e))
            else:
                self._raise_or_log(UNABLE_TO_GET_PATH_MSG.format(
                    path_for_err_reporting))
        return current_old_value

    def _simple_set_elem_value(self, obj, path_for_err_reporting, elem=None, value=None, action=None):
        """
        Set the element value directly on an object
        """
        try:
            if action == GET:
                try:
                    obj[elem] = value
                except IndexError:
                    if elem == len(obj):
                        obj.append(value)
                    else:
                        self._raise_or_log(ELEM_NOT_FOUND_TO_ADD_MSG.format(elem, path_for_err_reporting))
            elif action == GETATTR:
                setattr(obj, elem, value)
            else:
                raise DeltaError(INVALID_ACTION_WHEN_CALLING_SIMPLE_SET_ELEM.format(action))
        except (KeyError, IndexError, AttributeError, TypeError) as e:
            self._raise_or_log('Failed to set {} due to {}'.format(path_for_err_reporting, e))

    def _coerce_obj(self, parent, obj, path, parent_to_obj_elem,
                    parent_to_obj_action, elements, to_type, from_type):
        """
        Coerce obj and mark it in post_process_paths_to_convert for later to be converted back.
        Also reassign it to its parent to replace the old object.
        """
        self.post_process_paths_to_convert[elements[:-1]] = {'old_type': to_type, 'new_type': from_type}
        # If this function is going to ever be used to convert numpy arrays, uncomment these lines:
        # if from_type is np_ndarray:
        #     obj = obj.tolist()
        # else:
        obj = to_type(obj)

        if parent:
            # Making sure that the object is re-instated inside the parent especially if it was immutable
            # and we had to turn it into a mutable one. In such cases the object has a new id.
            self._simple_set_elem_value(obj=parent, path_for_err_reporting=path, elem=parent_to_obj_elem,
                                        value=obj, action=parent_to_obj_action)
        return obj

    def _set_new_value(self, parent, parent_to_obj_elem, parent_to_obj_action,
                       obj, elements, path, elem, action, new_value):
        """
        Set the element value on an object and if necessary convert the object to the proper mutable type
        """
        if isinstance(obj, tuple):
            # convert this object back to a tuple later
            obj = self._coerce_obj(
                parent, obj, path, parent_to_obj_elem,
                parent_to_obj_action, elements,
                to_type=list, from_type=tuple)
        self._simple_set_elem_value(obj=obj, path_for_err_reporting=path, elem=elem,
                                    value=new_value, action=action)

    def _simple_delete_elem(self, obj, path_for_err_reporting, elem=None, action=None):
        """
        Delete the element directly on an object
        """
        try:
            if action == GET:
                del obj[elem]
            elif action == GETATTR:
                del obj.__dict__[elem]
            else:
                raise DeltaError(INVALID_ACTION_WHEN_CALLING_SIMPLE_DELETE_ELEM.format(action))
        except (KeyError, IndexError, AttributeError) as e:
            self._raise_or_log('Failed to set {} due to {}'.format(path_for_err_reporting, e))

    def _del_elem(self, parent, parent_to_obj_elem, parent_to_obj_action,
                  obj, elements, path, elem, action):
        """
        Delete the element value on an object and if necessary convert the object to the proper mutable type
        """
        obj_is_new = False
        if isinstance(obj, tuple):
            # convert this object back to a tuple later
            self.post_process_paths_to_convert[elements[:-1]] = {'old_type': list, 'new_type': tuple}
            obj = list(obj)
            obj_is_new = True
        self._simple_delete_elem(obj=obj, path_for_err_reporting=path, elem=elem, action=action)
        if obj_is_new and parent:
            # Making sure that the object is re-instated inside the parent especially if it was immutable
            # and we had to turn it into a mutable one. In such cases the object has a new id.
            self._simple_set_elem_value(obj=parent, path_for_err_reporting=path, elem=parent_to_obj_elem,
                                        value=obj, action=parent_to_obj_action)

    def _do_iterable_item_added(self):
        iterable_item_added = self.diff.get('iterable_item_added')
        if iterable_item_added:
            self._do_item_added(iterable_item_added)

    def _do_dictionary_item_added(self):
        dictionary_item_added = self.diff.get('dictionary_item_added')
        if dictionary_item_added:
            self._do_item_added(dictionary_item_added)

    def _do_attribute_added(self):
        attribute_added = self.diff.get('attribute_added')
        if attribute_added:
            self._do_item_added(attribute_added)

    def _do_item_added(self, items):
        # sorting the items by their path so that the items with smaller index are applied first.
        for path, new_value in sorted(items.items(), key=lambda x: x[0]):
            elem_and_details = self._get_elements_and_details(path)
            if elem_and_details:
                elements, parent, parent_to_obj_elem, parent_to_obj_action, obj, elem, action = elem_and_details
            else:
                continue  # pragma: no cover. Due to cPython peephole optimizer, this line doesn't get covered. https://github.com/nedbat/coveragepy/issues/198
            self._set_new_value(parent, parent_to_obj_elem, parent_to_obj_action,
                                obj, elements, path, elem, action, new_value)

    def _do_values_changed(self):
        values_changed = self.diff.get('values_changed')
        if values_changed:
            self._do_values_or_type_changed(values_changed)

    def _do_type_changes(self):
        type_changes = self.diff.get('type_changes')
        if type_changes:
            self._do_values_or_type_changed(type_changes, is_type_change=True)

    def _do_post_process(self):
        if self.post_process_paths_to_convert:
            self._do_values_or_type_changed(self.post_process_paths_to_convert, is_type_change=True)

    def _do_pre_process(self):
        if self._numpy_paths and ('iterable_item_added' in self.diff or 'iterable_item_removed' in self.diff):
            preprocess_paths = dict_()
            for path, type_ in self._numpy_paths.items():
                preprocess_paths[path] = {'old_type': np_ndarray, 'new_type': list}
                try:
                    type_ = numpy_dtype_string_to_type(type_)
                except Exception as e:
                    self._raise_or_log(NOT_VALID_NUMPY_TYPE.format(e))
                    continue  # pragma: no cover. Due to cPython peephole optimizer, this line doesn't get covered. https://github.com/nedbat/coveragepy/issues/198
                self.post_process_paths_to_convert[path] = {'old_type': list, 'new_type': type_}
            if preprocess_paths:
                self._do_values_or_type_changed(preprocess_paths, is_type_change=True)

    def _get_elements_and_details(self, path):
        try:
            elements = _path_to_elements(path)
            if len(elements) > 1:
                parent = _get_nested_obj(obj=self, elements=elements[:-2])
                parent_to_obj_elem, parent_to_obj_action = elements[-2]
                obj = self._get_elem_and_compare_to_old_value(
                    obj=parent, path_for_err_reporting=path, expected_old_value=None,
                    elem=parent_to_obj_elem, action=parent_to_obj_action)
            else:
                parent = parent_to_obj_elem = parent_to_obj_action = None
                obj = _get_nested_obj(obj=self, elements=elements[:-1])
            elem, action = elements[-1]
        except Exception as e:
            self._raise_or_log(UNABLE_TO_GET_ITEM_MSG.format(path, e))
            return None
        else:
            if obj is not_found:
                return None
            return elements, parent, parent_to_obj_elem, parent_to_obj_action, obj, elem, action

    def _do_values_or_type_changed(self, changes, is_type_change=False):
        for path, value in changes.items():
            elem_and_details = self._get_elements_and_details(path)
            if elem_and_details:
                elements, parent, parent_to_obj_elem, parent_to_obj_action, obj, elem, action = elem_and_details
            else:
                continue  # pragma: no cover. Due to cPython peephole optimizer, this line doesn't get covered. https://github.com/nedbat/coveragepy/issues/198
            expected_old_value = value.get('old_value', not_found)

            current_old_value = self._get_elem_and_compare_to_old_value(
                obj=obj, path_for_err_reporting=path, expected_old_value=expected_old_value, elem=elem, action=action)
            if current_old_value is not_found:
                continue  # pragma: no cover. I have not been able to write a test for this case. But we should still check for it.
            # With type change if we could have originally converted the type from old_value
            # to new_value just by applying the class of the new_value, then we might not include the new_value
            # in the delta dictionary.
            if is_type_change and 'new_value' not in value:
                try:
                    new_type = value['new_type']
                    # in case of Numpy we pass the ndarray plus the dtype in a tuple
                    if new_type in numpy_dtypes:
                        new_value = np_array_factory(current_old_value, new_type)
                    else:
                        new_value = new_type(current_old_value)
                except Exception as e:
                    self._raise_or_log(TYPE_CHANGE_FAIL_MSG.format(obj[elem], value.get('new_type', 'unknown'), e))
                    continue
            else:
                new_value = value['new_value']

            self._set_new_value(parent, parent_to_obj_elem, parent_to_obj_action,
                                obj, elements, path, elem, action, new_value)

            self._do_verify_changes(path, expected_old_value, current_old_value)

    def _do_item_removed(self, items):
        """
        Handle removing items.
        """
        # Sorting the iterable_item_removed in reverse order based on the paths.
        # So that we delete a bigger index before a smaller index
        for path, expected_old_value in sorted(items.items(), key=lambda x: x[0], reverse=True):
            elem_and_details = self._get_elements_and_details(path)
            if elem_and_details:
                elements, parent, parent_to_obj_elem, parent_to_obj_action, obj, elem, action = elem_and_details
            else:
                continue  # pragma: no cover. Due to cPython peephole optimizer, this line doesn't get covered. https://github.com/nedbat/coveragepy/issues/198
            current_old_value = self._get_elem_and_compare_to_old_value(
                obj=obj, elem=elem, path_for_err_reporting=path, expected_old_value=expected_old_value, action=action)
            if current_old_value is not_found:
                continue
            self._del_elem(parent, parent_to_obj_elem, parent_to_obj_action,
                           obj, elements, path, elem, action)
            self._do_verify_changes(path, expected_old_value, current_old_value)

    def _do_iterable_item_removed(self):
        iterable_item_removed = self.diff.get('iterable_item_removed')
        if iterable_item_removed:
            self._do_item_removed(iterable_item_removed)

    def _do_dictionary_item_removed(self):
        dictionary_item_removed = self.diff.get('dictionary_item_removed')
        if dictionary_item_removed:
            self._do_item_removed(dictionary_item_removed)

    def _do_attribute_removed(self):
        attribute_removed = self.diff.get('attribute_removed')
        if attribute_removed:
            self._do_item_removed(attribute_removed)

    def _do_set_item_added(self):
        items = self.diff.get('set_item_added')
        if items:
            self._do_set_or_frozenset_item(items, func='union')

    def _do_set_item_removed(self):
        items = self.diff.get('set_item_removed')
        if items:
            self._do_set_or_frozenset_item(items, func='difference')

    def _do_set_or_frozenset_item(self, items, func):
        for path, value in items.items():
            elements = _path_to_elements(path)
            parent = _get_nested_obj(obj=self, elements=elements[:-1])
            elem, action = elements[-1]
            obj = self._get_elem_and_compare_to_old_value(
                parent, path_for_err_reporting=path, expected_old_value=None, elem=elem, action=action)
            new_value = getattr(obj, func)(value)
            self._simple_set_elem_value(parent, path_for_err_reporting=path, elem=elem, value=new_value, action=action)

    def _do_ignore_order_get_old(self, obj, remove_indexes_per_path, fixed_indexes_values, path_for_err_reporting):
        """
        A generator that gets the old values in an iterable when the order was supposed to be ignored.
        """
        old_obj_index = -1
        max_len = len(obj) - 1
        while old_obj_index < max_len:
            old_obj_index += 1
            current_old_obj = obj[old_obj_index]
            if current_old_obj in fixed_indexes_values:
                continue
            if old_obj_index in remove_indexes_per_path:
                expected_obj_to_delete = remove_indexes_per_path.pop(old_obj_index)
                if current_old_obj == expected_obj_to_delete:
                    continue
                else:
                    self._raise_or_log(FAIL_TO_REMOVE_ITEM_IGNORE_ORDER_MSG.format(
                        old_obj_index, path_for_err_reporting, expected_obj_to_delete, current_old_obj))
            yield current_old_obj

    def _do_ignore_order(self):
        """

            't1': [5, 1, 1, 1, 6],
            't2': [7, 1, 1, 1, 8],

            'iterable_items_added_at_indexes': {
                'root': {
                    0: 7,
                    4: 8
                }
            },
            'iterable_items_removed_at_indexes': {
                'root': {
                    4: 6,
                    0: 5
                }
            }

        """
        fixed_indexes = self.diff.get('iterable_items_added_at_indexes', dict_())
        remove_indexes = self.diff.get('iterable_items_removed_at_indexes', dict_())
        paths = set(fixed_indexes.keys()) | set(remove_indexes.keys())
        for path in paths:
            # In the case of ignore_order reports, we are pointing to the container object.
            # Thus we add a [0] to the elements so we can get the required objects and discard what we don't need.
            elem_and_details = self._get_elements_and_details("{}[0]".format(path))
            if elem_and_details:
                _, parent, parent_to_obj_elem, parent_to_obj_action, obj, _, _ = elem_and_details
            else:
                continue  # pragma: no cover. Due to cPython peephole optimizer, this line doesn't get covered. https://github.com/nedbat/coveragepy/issues/198
            # copying both these dictionaries since we don't want to mutate them.
            fixed_indexes_per_path = fixed_indexes.get(path, dict_()).copy()
            remove_indexes_per_path = remove_indexes.get(path, dict_()).copy()
            fixed_indexes_values = AnySet(fixed_indexes_per_path.values())

            new_obj = []
            # Numpy's NdArray does not like the bool function.
            if isinstance(obj, np_ndarray):
                there_are_old_items = obj.size > 0
            else:
                there_are_old_items = bool(obj)
            old_item_gen = self._do_ignore_order_get_old(
                obj, remove_indexes_per_path, fixed_indexes_values, path_for_err_reporting=path)
            while there_are_old_items or fixed_indexes_per_path:
                new_obj_index = len(new_obj)
                if new_obj_index in fixed_indexes_per_path:
                    new_item = fixed_indexes_per_path.pop(new_obj_index)
                    new_obj.append(new_item)
                elif there_are_old_items:
                    try:
                        new_item = next(old_item_gen)
                    except StopIteration:
                        there_are_old_items = False
                    else:
                        new_obj.append(new_item)
                else:
                    # pop a random item from the fixed_indexes_per_path dictionary
                    self._raise_or_log(INDEXES_NOT_FOUND_WHEN_IGNORE_ORDER.format(fixed_indexes_per_path))
                    new_item = fixed_indexes_per_path.pop(next(iter(fixed_indexes_per_path)))
                    new_obj.append(new_item)

            if isinstance(obj, tuple):
                new_obj = tuple(new_obj)
            # Making sure that the object is re-instated inside the parent especially if it was immutable
            # and we had to turn it into a mutable one. In such cases the object has a new id.
            self._simple_set_elem_value(obj=parent, path_for_err_reporting=path, elem=parent_to_obj_elem,
                                        value=new_obj, action=parent_to_obj_action)

    def dump(self, file):
        """
        Dump into file object
        """
        file.write(self.dumps())

    def dumps(self):
        """
        Return the serialized representation of the object as a bytes object, instead of writing it to a file.
        """
        return self.serializer(self.diff)

    def to_dict(self):
        return dict(self.diff)


if __name__ == "__main__":  # pragma: no cover
    import doctest
    doctest.testmod()
