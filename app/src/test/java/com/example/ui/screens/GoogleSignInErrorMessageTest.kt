package com.example.ui.screens

import android.content.Context
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleSignInErrorMessageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aPhoneWithNoGoogleAccountGetsAClearInstruction() {
        val message = googleSignInErrorMessage(context, NoCredentialException())

        assertEquals(context.getString(R.string.cloud_backup_no_google_account), message)
    }

    @Test
    fun otherFailuresShowTheirOwnMessage() {
        val message = googleSignInErrorMessage(context, IllegalStateException("Network is down"))

        assertEquals("Network is down", message)
    }

    @Test
    fun aFailureWithoutAMessageFallsBackToAGenericOne() {
        val message = googleSignInErrorMessage(context, IllegalStateException())

        assertEquals(context.getString(R.string.sign_in_failed), message)
    }
}
