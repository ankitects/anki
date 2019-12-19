from monkeytype.config import DefaultConfig
from monkeytype.typing import *

class MyConfig(DefaultConfig):
    def type_rewriter(self):
        rws = (RemoveEmptyContainers(),RewriteConfigDict(),RewriteLargeUnion(2))
        return ChainedRewriter(rws)

CONFIG = MyConfig()
