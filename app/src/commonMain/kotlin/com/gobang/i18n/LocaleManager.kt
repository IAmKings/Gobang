package com.gobang.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocaleManager {
    private val _language = MutableStateFlow("zh")
    val language: StateFlow<String> = _language.asStateFlow()

    val currentLanguage: String get() = _language.value

    fun setLanguage(lang: String) {
        _language.value = lang
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == "zh") "en" else "zh"
    }

    fun t(key: String, vararg args: Pair<String, Any>): String {
        return Strings.get(_language.value, key, *args)
    }
}