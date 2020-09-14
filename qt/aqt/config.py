from typing import Any

from aqt import mw


class ProfileConfig:
    """Can be used for profile-specific settings"""

    def __init__(self, keyword: str, default: Any):
        self.keyword = keyword
        self.default = default

    @property
    def value(self) -> Any:
        return mw.pm.profile.get(self.keyword, self.default)

    @value.setter
    def value(self, new_value: str):
        mw.pm.profile[self.keyword] = new_value

    def remove(self):
        try:
            del mw.pm.profile[self.keyword]
        except KeyError:
            # same behavior as Collection.remove_config
            pass


class MetaConfig:
    """Can be used for profile-agnostic settings"""

    def __init__(self, keyword: str, default: Any):
        self.keyword = keyword
        self.default = default

    @property
    def value(self) -> Any:
        return mw.pm.meta.get(self.keyword, self.default)

    @value.setter
    def value(self, new_value: str):
        mw.pm.meta[self.keyword] = new_value

    def remove(self):
        try:
            del mw.pm.meta[self.keyword]
        except KeyError:
            # same behavior as Collection.remove_config
            pass


class CollectionConfig:
    """Can be used for collection-specific settings"""

    def __init__(self, keyword: str, default: Any):
        self.keyword = keyword
        self.default = default

    @property
    def value(self):
        return mw.col.get_config(self.keyword, self.default)

    @value.setter
    def value(self, new_value: str):
        mw.col.set_config(self.keyword, new_value)

    def remove(self):
        return mw.col.remove_config(self.keyword)
