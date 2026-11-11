package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Owns every ViewModel that belongs to the signed-in user. It survives configuration
 * changes (it lives in the activity's store) and is cleared on sign-out, which cancels
 * streams and listeners and removes cached files.
 */
class UserScope : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /** False after an explicit sign-out so the Google prompt does not reappear on its own. */
    var autoSignIn = true

    fun reset() {
        viewModelStore.clear()
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
