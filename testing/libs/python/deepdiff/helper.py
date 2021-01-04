import sys
import re
import os
import datetime
import logging
import warnings
import time
from ast import literal_eval
from decimal import Decimal, localcontext
from collections import namedtuple, OrderedDict
from itertools import repeat
from ordered_set import OrderedSet
from threading import Timer


class np_type:
    pass


try:
    import numpy as np
except ImportError:  # pragma: no cover. The case without Numpy is tested locally only.
    np = None  # pragma: no cover.
    np_array_factory = 'numpy not available'  # pragma: no cover.
    np_ndarray = np_type  # pragma: no cover.
    np_bool_ = np_type  # pragma: no cover.
    np_int8 = np_type  # pragma: no cover.
    np_int16 = np_type  # pragma: no cover.
    np_int32 = np_type  # pragma: no cover.
    np_int64 = np_type  # pragma: no cover.
    np_uint8 = np_type  # pragma: no cover.
    np_uint16 = np_type  # pragma: no cover.
    np_uint32 = np_type  # pragma: no cover.
    np_uint64 = np_type  # pragma: no cover.
    np_intp = np_type  # pragma: no cover.
    np_uintp = np_type  # pragma: no cover.
    np_float32 = np_type  # pragma: no cover.
    np_float64 = np_type  # pragma: no cover.
    np_float_ = np_type  # pragma: no cover.
    np_complex64 = np_type  # pragma: no cover.
    np_complex128 = np_type  # pragma: no cover.
    np_complex_ = np_type  # pragma: no cover.
else:
    np_array_factory = np.array
    np_ndarray = np.ndarray
    np_bool_ = np.bool_
    np_int8 = np.int8
    np_int16 = np.int16
    np_int32 = np.int32
    np_int64 = np.int64
    np_uint8 = np.uint8
    np_uint16 = np.uint16
    np_uint32 = np.uint32
    np_uint64 = np.uint64
    np_intp = np.intp
    np_uintp = np.uintp
    np_float32 = np.float32
    np_float64 = np.float64
    np_float_ = np.float_
    np_complex64 = np.complex64
    np_complex128 = np.complex128
    np_complex_ = np.complex_

numpy_numbers = (
    np_int8, np_int16, np_int32, np_int64, np_uint8,
    np_uint16, np_uint32, np_uint64, np_intp, np_uintp,
    np_float32, np_float64, np_float_, np_complex64,
    np_complex128, np_complex_, )

numpy_dtypes = set(numpy_numbers)
numpy_dtypes.add(np_bool_)

numpy_dtype_str_to_type = {
    item.__name__: item for item in numpy_dtypes
}

logger = logging.getLogger(__name__)

py_major_version = sys.version_info.major
py_minor_version = sys.version_info.minor

py_current_version = Decimal("{}.{}".format(py_major_version, py_minor_version))

py2 = py_major_version == 2
py3 = py_major_version == 3
py4 = py_major_version == 4

MINIMUM_PY_DICT_TYPE_SORTED = Decimal('3.6')
DICT_IS_SORTED = py_current_version >= MINIMUM_PY_DICT_TYPE_SORTED


class OrderedDictPlus(OrderedDict):
    """
    This class is only used when a python version is used where
    the built-in dictionary is not ordered.
    """

    def __repr__(self):  # pragma: no cover. Only used in pypy3 and py3.5
        return str(dict(self))  # pragma: no cover. Only used in pypy3 and py3.5

    __str__ = __repr__

    def copy(self):  # pragma: no cover. Only used in pypy3 and py3.5
        result = OrderedDictPlus()  # pragma: no cover. Only used in pypy3 and py3.5
        for k, v in self.items():  # pragma: no cover. Only used in pypy3 and py3.5
            result[k] = v  # pragma: no cover. Only used in pypy3 and py3.5
        return result  # pragma: no cover. Only used in pypy3 and py3.5


if DICT_IS_SORTED:
    dict_ = dict
else:
    dict_ = OrderedDictPlus  # pragma: no cover. Only used in pypy3 and py3.5


if py4:
    logger.warning('Python 4 is not supported yet. Switching logic to Python 3.')  # pragma: no cover
    py3 = True  # pragma: no cover

if py2:  # pragma: no cover
    sys.exit('Python 2 is not supported anymore. The last version of DeepDiff that supported Py2 was 3.3.0')

pypy3 = py3 and hasattr(sys, "pypy_translation_info")
py3_5 = py_current_version == Decimal('3.5')

strings = (str, bytes)  # which are both basestring
unicode_type = str
bytes_type = bytes
only_numbers = (int, float, complex, Decimal) + numpy_numbers
datetimes = (datetime.datetime, datetime.date, datetime.timedelta, datetime.time)
times = (datetime.datetime, datetime.time)
numbers = only_numbers + datetimes
booleans = (bool, np_bool_)

IndexedHash = namedtuple('IndexedHash', 'indexes item')

current_dir = os.path.dirname(os.path.abspath(__file__))

ID_PREFIX = '!>*id'

ZERO_DECIMAL_CHARACTERS = set("-0.")

KEY_TO_VAL_STR = "{}:{}"

TREE_VIEW = 'tree'
TEXT_VIEW = 'text'
DELTA_VIEW = '_delta'


def short_repr(item, max_length=15):
    """Short representation of item if it is too long"""
    item = repr(item)
    if len(item) > max_length:
        item = '{}...{}'.format(item[:max_length - 3], item[-1])
    return item


class ListItemRemovedOrAdded:  # pragma: no cover
    """Class of conditions to be checked"""
    pass


class OtherTypes:
    def __repr__(self):
        return "Error: {}".format(self.__class__.__name__)  # pragma: no cover

    __str__ = __repr__


class Skipped(OtherTypes):
    pass


class Unprocessed(OtherTypes):
    pass


class NotHashed(OtherTypes):
    pass


class NotPresent:  # pragma: no cover
    """
    In a change tree, this indicated that a previously existing object has been removed -- or will only be added
    in the future.
    We previously used None for this but this caused problem when users actually added and removed None. Srsly guys? :D
    """
    def __repr__(self):
        return 'not present'  # pragma: no cover

    __str__ = __repr__


unprocessed = Unprocessed()
skipped = Skipped()
not_hashed = NotHashed()
notpresent = NotPresent()


# Disabling remapping from old to new keys since the mapping is deprecated.
RemapDict = dict_


# class RemapDict(dict_):
#     """
#     DISABLED
#     Remap Dictionary.

#     For keys that have a new, longer name, remap the old key to the new key.
#     Other keys that don't have a new name are handled as before.
#     """

#     def __getitem__(self, old_key):
#         new_key = EXPANDED_KEY_MAP.get(old_key, old_key)
#         if new_key != old_key:
#             logger.warning(
#                 "DeepDiff Deprecation: %s is renamed to %s. Please start using "
#                 "the new unified naming convention.", old_key, new_key)
#         if new_key in self:
#             return self.get(new_key)
#         else:  # pragma: no cover
#             raise KeyError(new_key)


class indexed_set(set):
    """
    A set class that lets you get an item by index

    >>> a = indexed_set()
    >>> a.add(10)
    >>> a.add(20)
    >>> a[0]
    10
    """


JSON_CONVERTOR = {
    Decimal: float,
    OrderedSet: list,
    type: lambda x: x.__name__,
    bytes: lambda x: x.decode('utf-8')
}


def json_convertor_default(default_mapping=None):
    _convertor_mapping = JSON_CONVERTOR.copy()
    if default_mapping:
        _convertor_mapping.update(default_mapping)

    def _convertor(obj):
        for original_type, convert_to in _convertor_mapping.items():
            if isinstance(obj, original_type):
                return convert_to(obj)
        raise TypeError('We do not know how to convert {} of type {} for json serialization. Please pass the default_mapping parameter with proper mapping of the object to a basic python type.'.format(obj, type(obj)))

    return _convertor


def add_to_frozen_set(parents_ids, item_id):
    return parents_ids | {item_id}


def convert_item_or_items_into_set_else_none(items):
    if items:
        if isinstance(items, strings):
            items = {items}
        else:
            items = set(items)
    else:
        items = None
    return items


RE_COMPILED_TYPE = type(re.compile(''))


def convert_item_or_items_into_compiled_regexes_else_none(items):
    if items:
        if isinstance(items, (strings, RE_COMPILED_TYPE)):
            items = [items]
        items = [i if isinstance(i, RE_COMPILED_TYPE) else re.compile(i) for i in items]
    else:
        items = None
    return items


def get_id(obj):
    """
    Adding some characters to id so they are not just integers to reduce the risk of collision.
    """
    return "{}{}".format(ID_PREFIX, id(obj))


def get_type(obj):
    """
    Get the type of object or if it is a class, return the class itself.
    """
    if isinstance(obj, np_ndarray):
        return obj.dtype.type
    return obj if type(obj) is type else type(obj)


def numpy_dtype_string_to_type(dtype_str):
    return numpy_dtype_str_to_type[dtype_str]


def type_in_type_group(item, type_group):
    return get_type(item) in type_group


def type_is_subclass_of_type_group(item, type_group):
    return isinstance(item, type_group) \
           or (isinstance(item, type) and issubclass(item, type_group)) \
           or type_in_type_group(item, type_group)


def get_doc(doc_filename):
    try:
        with open(os.path.join(current_dir, '../docs/', doc_filename), 'r') as doc_file:
            doc = doc_file.read()
    except Exception:  # pragma: no cover
        doc = 'Failed to load the docstrings. Please visit: https://zepworks.com/deepdiff/current/'  # pragma: no cover
    return doc


number_formatting = {
    "f": r'{:.%sf}',
    "e": r'{:.%se}',
}


def number_to_string(number, significant_digits, number_format_notation="f"):
    """
    Convert numbers to string considering significant digits.
    """
    try:
        using = number_formatting[number_format_notation]
    except KeyError:
        raise ValueError("number_format_notation got invalid value of {}. The valid values are 'f' and 'e'".format(number_format_notation)) from None
    if isinstance(number, Decimal):
        tup = number.as_tuple()
        with localcontext() as ctx:
            ctx.prec = len(tup.digits) + tup.exponent + significant_digits
            number = number.quantize(Decimal('0.' + '0' * significant_digits))
    elif not isinstance(number, numbers):
        return number
    result = (using % significant_digits).format(number)
    # Special case for 0: "-0.00" should compare equal to "0.00"
    if set(result) <= ZERO_DECIMAL_CHARACTERS:
        result = "0.00"
    # https://bugs.python.org/issue36622
    if number_format_notation == 'e' and isinstance(number, float):
        result = result.replace('+0', '+')
    return result


class DeepDiffDeprecationWarning(DeprecationWarning):
    """
    Use this warning instead of DeprecationWarning
    """
    pass


def cartesian_product(a, b):
    """
    Get the Cartesian product of two iterables

    **parameters**

    a: list of lists
    b: iterable to do the Cartesian product
    """

    for i in a:
        for j in b:
            yield i + (j,)


def cartesian_product_of_shape(dimentions, result=None):
    """
    Cartesian product of a dimentions iterable.
    This is mainly used to traverse Numpy ndarrays.

    Each array has dimentions that are defines in ndarray.shape
    """
    if result is None:
        result = ((),)  # a tuple with an empty tuple
    for dimension in dimentions:
        result = cartesian_product(result, range(dimension))
    return result


def get_numpy_ndarray_rows(obj, shape=None):
    """
    Convert a multi dimensional numpy array to list of rows
    """
    if shape is None:
        shape = obj.shape

    dimentions = shape[:-1]
    for path_tuple in cartesian_product_of_shape(dimentions):
        result = obj
        for index in path_tuple:
            result = result[index]
        yield path_tuple, result


class _NotFound:

    def __eq__(self, other):
        return False

    __req__ = __eq__

    def __repr__(self):
        return 'not found'

    __str__ = __repr__


not_found = _NotFound()


warnings.simplefilter('once', DeepDiffDeprecationWarning)


class OrderedSetPlus(OrderedSet):

    def lpop(self):
        """
        Remove and return the first element from the set.
        Raises KeyError if the set is empty.
        Example:
            >>> oset = OrderedSet([1, 2, 3])
            >>> oset.lpop()
            1
        """
        if not self.items:
            raise KeyError('lpop from an empty set')

        elem = self.items[0]
        del self.items[0]
        del self.map[elem]
        return elem


class RepeatedTimer:
    """
    Threaded Repeated Timer by MestreLion
    https://stackoverflow.com/a/38317060/1497443
    """

    def __init__(self, interval, function, *args, **kwargs):
        self._timer = None
        self.interval = interval
        self.function = function
        self.args = args
        self.start_time = time.time()
        self.kwargs = kwargs
        self.is_running = False
        self.start()

    def _get_duration_sec(self):
        return int(time.time() - self.start_time)

    def _run(self):
        self.is_running = False
        self.start()
        self.function(*self.args, **self.kwargs)

    def start(self):
        self.kwargs.update(duration=self._get_duration_sec())
        if not self.is_running:
            self._timer = Timer(self.interval, self._run)
            self._timer.start()
            self.is_running = True

    def stop(self):
        duration = self._get_duration_sec()
        self._timer.cancel()
        self.is_running = False
        return duration


LITERAL_EVAL_PRE_PROCESS = [
    ('Decimal(', ')', Decimal),
]


def literal_eval_extended(item):
    """
    An extend version of literal_eval
    """
    try:
        return literal_eval(item)
    except (SyntaxError, ValueError):
        for begin, end, func in LITERAL_EVAL_PRE_PROCESS:
            if item.startswith(begin) and item.endswith(end):
                # Extracting and removing extra quotes so for example "Decimal('10.1')" becomes "'10.1'" and then '10.1'
                item2 = item[len(begin): -len(end)].strip('\'\"')
                return func(item2)
        raise


def time_to_seconds(t):
    return (t.hour * 60 + t.minute) * 60 + t.second


def datetime_normalize(truncate_datetime, obj):
    if truncate_datetime:
        if truncate_datetime == 'second':
            obj = obj.replace(microsecond=0)
        elif truncate_datetime == 'minute':
            obj = obj.replace(second=0, microsecond=0)
        elif truncate_datetime == 'hour':
            obj = obj.replace(minute=0, second=0, microsecond=0)
        elif truncate_datetime == 'day':
            obj = obj.replace(hour=0, minute=0, second=0, microsecond=0)
    if isinstance(obj, datetime.datetime):
        obj = obj.replace(tzinfo=datetime.timezone.utc)
    elif isinstance(obj, datetime.time):
        obj = time_to_seconds(obj)
    return obj


def get_truncate_datetime(truncate_datetime):
    """
    Validates truncate_datetime value
    """
    if truncate_datetime not in {None, 'second', 'minute', 'hour', 'day'}:
        raise ValueError("truncate_datetime must be second, minute, hour or day")
    return truncate_datetime


def cartesian_product_numpy(*arrays):
    """
    Cartesian product of Numpy arrays by Paul Panzer
    https://stackoverflow.com/a/49445693/1497443
    """
    la = len(arrays)
    dtype = np.result_type(*arrays)
    arr = np.empty((la, *map(len, arrays)), dtype=dtype)
    idx = slice(None), *repeat(None, la)
    for i, a in enumerate(arrays):
        arr[i, ...] = a[idx[:la - i]]
    return arr.reshape(la, -1).T


def diff_numpy_array(A, B):
    """
    Numpy Array A - B
    return items in A that are not in B
    By Divakar
    https://stackoverflow.com/a/52417967/1497443
    """
    return A[~np.in1d(A, B)]


PYTHON_TYPE_TO_NUMPY_TYPE = {
    int: np_int64,
    float: np_float64,
    Decimal: np_float64
}


def get_homogeneous_numpy_compatible_type_of_seq(seq):
    """
    Return with the numpy dtype if the array can be converted to a non-object numpy array.
    Originally written by mgilson https://stackoverflow.com/a/13252348/1497443
    This is the modified version.
    """
    iseq = iter(seq)
    first_type = type(next(iseq))
    if first_type in {int, float, Decimal}:
        type_ = first_type if all((type(x) is first_type) for x in iseq ) else False
        return PYTHON_TYPE_TO_NUMPY_TYPE.get(type_, False)
    else:
        return False
