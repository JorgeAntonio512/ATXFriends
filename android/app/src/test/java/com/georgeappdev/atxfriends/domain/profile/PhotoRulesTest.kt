package com.georgeappdev.atxfriends.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRulesTest {

    @Test
    fun exactlyThreeSlots_withIosFileNames() {
        assertEquals(3, PhotoRules.SLOT_COUNT)
        assertEquals("uid_photo_0.jpg", PhotoRules.fileName("uid", 0))
        assertEquals("profile_photos/uid/uid_photo_2.jpg", PhotoRules.storagePath("uid", 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun noFourthSlot() {
        PhotoRules.fileName("uid", 3)
    }

    @Test
    fun sizeLimit_isUnderTenMegabytes() {
        assertTrue(PhotoRules.isUnderSizeLimit(10 * 1024 * 1024 - 1))
        assertFalse(PhotoRules.isUnderSizeLimit(10 * 1024 * 1024))
    }

    @Test
    fun jpegSettingsMatchIos() {
        assertEquals(85, PhotoRules.JPEG_QUALITY)
        assertEquals("image/jpeg", PhotoRules.CONTENT_TYPE)
    }

    @Test
    fun resize_fitsLongestSideTo1024_keepingAspect_andNeverUpscales() {
        assertEquals(1024 to 768, PhotoRules.targetSize(4032, 3024))
        assertEquals(768 to 1024, PhotoRules.targetSize(3024, 4032))
        assertEquals(1024 to 1024, PhotoRules.targetSize(2000, 2000))
        assertEquals(800 to 600, PhotoRules.targetSize(800, 600))
        assertEquals(1024 to 1, PhotoRules.targetSize(5000, 2))
    }

    @Test
    fun replacingOneSlot_keepsTheOthers_andPadsMissingOnes() {
        assertEquals(listOf("a", "NEW", "c"), PhotoRules.replacing(listOf("a", "b", "c"), 1, "NEW"))
        assertEquals(listOf("a", "", "NEW"), PhotoRules.replacing(listOf("a"), 2, "NEW"))
    }
}
