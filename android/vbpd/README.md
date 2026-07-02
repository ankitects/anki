## ViewBindingPropertyDelegate (`vbpd`)

Simplifies [view bindings](https://developer.android.com/topic/libraries/view-binding):

* Manages ViewBinding lifecycle and clears the reference to it to prevent memory leaks
* Eliminates the need to keep nullable references to Views or ViewBindings
* Creates ViewBinding lazily

### Implementation notes

This library has been vendored to reduce the number of dependencies, and to remove code we will not use

The reflection-based capabilities of this library are removed, to focus on performance

Original source: https://github.com/androidbroadcast/ViewBindingPropertyDelegate/tree/73c87714df0d3e464a68c6e5f149e775fcd75bba