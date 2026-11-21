package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.ParcelHubRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ParcelHub MA", appName)
  }

  @Test
  fun `merchant can create new account sign out and sign back in`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ParcelHubRepository(context)

    val uniqueEmail = "youness_${System.currentTimeMillis()}@shop.ma"
    val signUpRes = repo.signUpSupabase(
      email = uniqueEmail,
      password = "secret123",
      storeBrandName = "Youness Express"
    )
    assertTrue(signUpRes.isSuccess)
    assertEquals(uniqueEmail, repo.snapshot.value.session.email)
    assertEquals("Youness Express", repo.snapshot.value.session.storeBrandName)
    assertNotNull(repo.snapshot.value.session.accessToken)

    // Duplicate signUp should fail cleanly
    val dupRes = repo.signUpSupabase(
      email = uniqueEmail,
      password = "secret123",
      storeBrandName = "Other Store"
    )
    assertTrue(dupRes.isFailure)

    // Sign out resets to default session
    repo.signOut()
    assertEquals("atlas.shop@parcelhub.ma", repo.snapshot.value.session.email)

    // Wrong password fails
    val wrongPwd = repo.signInSupabase(uniqueEmail, "wrongpass")
    assertTrue(wrongPwd.isFailure)

    // Correct password restores account and store name
    val signInRes = repo.signInSupabase(uniqueEmail, "secret123")
    assertTrue(signInRes.isSuccess)
    assertEquals(uniqueEmail, repo.snapshot.value.session.email)
    assertEquals("Youness Express", repo.snapshot.value.session.storeBrandName)

    // Fresh repository instance restores session from DataStore
    val repo2 = ParcelHubRepository(context)
    repo2.restoreSessionFromPrefs()
    assertEquals(uniqueEmail, repo2.snapshot.value.session.email)
    assertEquals("Youness Express", repo2.snapshot.value.session.storeBrandName)
  }
}
