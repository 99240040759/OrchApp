package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class UserScope : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    var autoSignIn = true

    fun reset() {
        viewModelStore.clear()
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
