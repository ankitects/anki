import json
from typing import List
from deepdiff import DeepDiff
import numbers


class IntegerConverter:
    def convert(self, obj):
        return int(obj)


class StringConverter:
    def convert(self, obj):
        return str(obj)


class FloatConverter:
    def convert(self, obj):
        return float(obj)

class BoolConverter:
    def convert(self, obj):
        return bool(obj)

class ListConverter:
    def __init__(self, converter):
        self.converter = converter
    def convert(self, obj):
        if not isinstance(obj, List):
            raise Exception('cannot deserialize non-list to list')
        return [self.converter.convert(x) for x in obj]


class UserTypeConverter:
    def __init__(self, inner_converters, user_type):
        self.inner_converters = inner_converters
        self.user_type = user_type

    def convert(self, obj):
        return self.user_type(*[self.inner_converters[i].convert(x) for i, x in enumerate(obj)])


class TestCase:
    def __init__(self, args, expected):
        self.args = args
        self.expected = expected

def parse_test_case(converters, line):
    cols = line.split(';')
    values = []
    for i, col in enumerate(cols):
        values.append(converters[i].convert(json.loads(col)))
    return TestCase(values[:-1], values[-1])

def compare(obj1, obj2):
    are_equal = True
    ddiff = DeepDiff(obj1, obj2, ignore_order=True)
    if 'values_changed' in ddiff:
        for key in ddiff['values_changed']:
            new_val = ddiff['values_changed'][key]['new_value']
            old_val = ddiff['values_changed'][key]['old_value']
            if isinstance(new_val, numbers.Number) and isinstance(old_val, numbers.Number):
                if abs(old_val - new_val) < 0.0001:
                    continue
            are_equal = False
            break
    return are_equal
