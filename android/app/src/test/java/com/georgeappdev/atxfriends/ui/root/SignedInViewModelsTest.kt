package com.georgeappdev.atxfriends.ui.root

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedInViewModelsTest {

    class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun signingOut_clearsEveryViewModelTheSignedInUserCreated() {
        val holder = SignedInViewModels(Application())
        val probe = ViewModelProvider(holder)[Probe::class.java]
        assertFalse(probe.cleared)

        holder.clear()

        assertTrue(probe.cleared)
        assertNotSame("the next user starts with fresh view models", probe, ViewModelProvider(holder)[Probe::class.java])
    }
}
