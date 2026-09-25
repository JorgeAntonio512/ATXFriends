package com.georgeappdev.atxfriends.ui.root

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

/** Keeps each visit's ViewModelStore across rotation (it lives in the Activity's store). */
class VisitStores : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun get(id: String): ViewModelStore = stores.getOrPut(id) { ViewModelStore() }

    fun clear(id: String) {
        stores.remove(id)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/**
 * Screens RootScreen shows outside a NavHost (the pending-account gate, profile setup) get
 * ViewModels scoped to one visit: they survive rotation, and are cleared — coroutines and
 * collectors stopped — as soon as the screen leaves, so nothing from an old visit can still act.
 */
@Composable
fun VisitScope(content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "VisitScope needs a ViewModelStoreOwner" }
    val stores = viewModel<VisitStores>(parent)
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val activity = LocalContext.current as? Activity
    val owner = remember(id) {
        // Pass the Activity's creation extras through, so factories can read the Application.
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore: ViewModelStore = stores.get(id)
            override val defaultViewModelProviderFactory: ViewModelProvider.Factory
                get() = (parent as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
                    ?: ViewModelProvider.NewInstanceFactory()
            override val defaultViewModelCreationExtras: CreationExtras
                get() = (parent as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
                    ?: CreationExtras.Empty
        }
    }
    DisposableEffect(id) {
        onDispose { if (activity?.isChangingConfigurations != true) stores.clear(id) }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
