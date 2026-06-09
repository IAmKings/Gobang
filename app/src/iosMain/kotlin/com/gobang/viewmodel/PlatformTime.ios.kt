package com.gobang.viewmodel

import platform.Foundation.NSDate
import platform.Foundation.NSDateWithTimeIntervalSince1970

actual fun epochMillis(): Long {
    val date = NSDate()
    return (date.timeIntervalSince1970 * 1000.0).toLong()
}