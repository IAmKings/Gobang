package com.gobang.viewmodel

import kotlinx.datetime.Clock

actual fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()