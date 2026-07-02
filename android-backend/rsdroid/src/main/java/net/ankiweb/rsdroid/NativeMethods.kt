package net.ankiweb.rsdroid

import androidx.annotation.CheckResult

object NativeMethods {
    @CheckResult
    external fun runMethodRaw(
        backendPointer: Long,
        service: Int,
        method: Int,
        args: ByteArray,
    ): Array<ByteArray?>?

    @CheckResult
    external fun openBackend(data: ByteArray): Array<ByteArray?>?

    external fun closeBackend(backendPointer: Long)
}
