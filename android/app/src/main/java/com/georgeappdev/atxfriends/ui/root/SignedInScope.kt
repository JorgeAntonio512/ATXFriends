package com.georgeappdev.atxfriends.ui.root

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Holds every ViewModel created while a user is signed in (the tabs' nav back stacks live in
 * it), and throws them all away when the signed-in UI leaves — on sign-out or account
 * deletion — but not on rotation. The iOS equivalent is MainTabView and its view models being
 * released when RootView switches back to onboarding: nothing the last user loaded stays in
 * memory for the next one.
 */
class SignedInViewModels(private val app: Application) : ViewModel(), ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override var viewModelStore = ViewModelStore()
        private set

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = ViewModelProvider.AndroidViewModelFactory(app)

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply { set(APPLICATION_KEY, app) }

    fun clear() {
        viewModelStore.clear()
        viewModelStore = ViewModelStore()
    }

    override fun onCleared() = viewModelStore.clear()
}

@Composable
fun SignedInScope(content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    val holder: SignedInViewModels = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SignedInViewModels(checkNotNull(extras[APPLICATION_KEY])) as T
            }
        },
    )
    DisposableEffect(holder) {
        onDispose { if (activity?.isChangingConfigurations != true) holder.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides holder, content = content)
}
