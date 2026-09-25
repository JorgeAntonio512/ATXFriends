package com.georgeappdev.atxfriends.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StayConnectedRulesTest {
    @Test
    fun offeredOnce_onlyWhileThePermissionIsUndecided_onAndroid13Plus() {
        assertTrue(StayConnectedRules.shouldOffer(alreadyOffered = false, needsRuntimePermission = true, granted = false, systemPromptShown = false))
        assertFalse("never twice", StayConnectedRules.shouldOffer(true, true, false, false))
        assertFalse("before Android 13 there's nothing to ask", StayConnectedRules.shouldOffer(false, false, false, false))
        assertFalse("already allowed", StayConnectedRules.shouldOffer(false, true, true, false))
        assertFalse("already answered in Settings", StayConnectedRules.shouldOffer(false, true, false, true))
    }
}
