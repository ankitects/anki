class TestArg:
    """
    This class represents an argument of a solution function. It stores:
       - argument name
       - argument type name
    """
    def __init__(self, arg_type: str, arg_name: str):
        self.arg_type = arg_type
        self.arg_name = arg_name
