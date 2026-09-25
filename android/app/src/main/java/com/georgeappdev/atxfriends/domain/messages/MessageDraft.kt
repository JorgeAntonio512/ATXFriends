package com.georgeappdev.atxfriends.domain.messages

/**
 * What the input bar's text becomes when sent. iOS trims whitespace and newlines and sends
 * nothing when that leaves an empty string (MessagingViewModel.sendMessage). Firestore rules
 * also cap `text` at 5000 characters; iOS doesn't check, so a longer message just fails to
 * send there. Android checks first and tells the user instead.
 */
object MessageDraft {
    /** The rules' `text.size() <= 5000`, counted in Unicode characters as the rules do. */
    const val MAX_LENGTH = 5000

    sealed interface Result {
        data class Ready(val text: String) : Result
        data object Empty : Result
        data class TooLong(val length: Int) : Result
    }

    fun prepare(draft: String): Result {
        val text = draft.trim()
        if (text.isEmpty()) return Result.Empty
        val length = text.codePointCount(0, text.length)
        return if (length > MAX_LENGTH) Result.TooLong(length) else Result.Ready(text)
    }

    /**
     * Puts an unsent message back in the input box after a failed send. iOS replaces the draft
     * with it; Android keeps anything typed since by putting the failed text in front.
     */
    fun restore(failedText: String, currentDraft: String): String =
        if (currentDraft.isBlank()) failedText else "$failedText\n$currentDraft"
}
