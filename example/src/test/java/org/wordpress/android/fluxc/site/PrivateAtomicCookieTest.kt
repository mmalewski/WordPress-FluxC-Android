package org.wordpress.android.fluxc.site

import android.content.SharedPreferences
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.network.rest.wpcom.site.PrivateAtomicCookie
import org.wordpress.android.fluxc.network.rest.wpcom.site.PrivateAtomicCookie.Companion.PRIVATE_ATOMIC_COOKIE_PREF_KEY
import org.wordpress.android.fluxc.network.rest.wpcom.site.AtomicCookie
import org.wordpress.android.fluxc.utils.PreferenceUtils.PreferenceUtilsWrapper

@RunWith(MockitoJUnitRunner::class)
class PrivateAtomicCookieTest {
    @Mock lateinit var sharedPreferences: SharedPreferences
    @Mock lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    @Mock lateinit var preferenceUtilsWrapper: PreferenceUtilsWrapper
    private lateinit var privateAtomicCookie: PrivateAtomicCookie

    private var testCookie = AtomicCookie("1586725400", "/", "wordrpess.org", "cookie_name", "cookie_value")
    private val testCookieAsJsonString = "{\"expires\":\"1586725400\",\"path\":\"/\",\"domain\":\"wordrpess.org\"," +
            "\"name\":\"cookie_name\",\"value\":\"cookie_value\"}"

    @Before
    fun setUp() {
        whenever(preferenceUtilsWrapper.getFluxCPreferences()).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(sharedPreferencesEditor)
        whenever(sharedPreferencesEditor.putString(any(), any())).thenReturn(sharedPreferencesEditor)
        whenever(sharedPreferencesEditor.remove(any())).thenReturn(sharedPreferencesEditor)

        privateAtomicCookie = PrivateAtomicCookie(preferenceUtilsWrapper)
    }

    @Test
    fun `setting cookie stores it in memory and shared preferences`() {
        privateAtomicCookie.set(testCookie)

        verify(sharedPreferences).edit()
        Mockito.inOrder(sharedPreferencesEditor).apply {
            this.verify(sharedPreferencesEditor).putString(PRIVATE_ATOMIC_COOKIE_PREF_KEY, testCookieAsJsonString)
            this.verify(sharedPreferencesEditor).apply()
        }

        assertThat(privateAtomicCookie.exists()).isTrue()
        assertThat(privateAtomicCookie.getDomain()).isEqualTo("wordrpess.org")
        assertThat(privateAtomicCookie.getExpirationDateEpoch()).isEqualTo("1586725400")
        assertThat(privateAtomicCookie.getName()).isEqualTo("cookie_name")
        assertThat(privateAtomicCookie.getValue()).isEqualTo("cookie_value")
    }

    @Test
    fun `clearing cookie removes it from memory and shared preferences`() {
        privateAtomicCookie.set(testCookie)
        assertThat(privateAtomicCookie.exists()).isTrue()

        privateAtomicCookie.clearCookie()

        verify(sharedPreferences, times(2)).edit()
        Mockito.inOrder(sharedPreferencesEditor).apply {
            this.verify(sharedPreferencesEditor).remove(PRIVATE_ATOMIC_COOKIE_PREF_KEY)
            this.verify(sharedPreferencesEditor).apply()
        }

        assertThat(privateAtomicCookie.exists()).isFalse()
    }

    @Test
    fun `cookie expires if it's expiration time is before current time`() {
        val currentTime = System.currentTimeMillis() / 1000
        val cookieExpirationTime = currentTime - 3600 // cookie expired one hour ago

        privateAtomicCookie.set(getCookieWithSpecificExpirationTime(cookieExpirationTime))
        assertThat(privateAtomicCookie.exists()).isTrue()
        assertThat(privateAtomicCookie.isExpired()).isTrue()
    }

    @Test
    fun `cookie is not expired if it's expiration time if after current time`() {
        val currentTime = System.currentTimeMillis() / 1000
        val cookieExpirationTime = currentTime + 3600 // cookie expires in one hour

        privateAtomicCookie.set(getCookieWithSpecificExpirationTime(cookieExpirationTime))
        assertThat(privateAtomicCookie.exists()).isTrue()
        assertThat(privateAtomicCookie.isExpired()).isFalse()
    }

    @Test
    fun `cookie expires soon if it's expiration time is within 6 hours from now`() {
        val currentTime = System.currentTimeMillis() / 1000
        val cookieExpirationTime = currentTime + 3600 * 6 // cookie will expire in 6 hours

        privateAtomicCookie.set(getCookieWithSpecificExpirationTime(cookieExpirationTime))
        assertThat(privateAtomicCookie.exists()).isTrue()
        assertThat(privateAtomicCookie.isExpired()).isFalse()
        assertThat(privateAtomicCookie.isCookieRefreshRequired()).isTrue()
    }

    @Test
    fun `cookie is not expiring soon if it's expiration time is more than 6 hours from now`() {
        val currentTime = System.currentTimeMillis() / 1000
        val cookieExpirationTime = currentTime + 3600 * 7 // cookie will expire in 7 hours

        privateAtomicCookie.set(getCookieWithSpecificExpirationTime(cookieExpirationTime))
        assertThat(privateAtomicCookie.exists()).isTrue()
        assertThat(privateAtomicCookie.isExpired()).isFalse()
        assertThat(privateAtomicCookie.isCookieRefreshRequired()).isFalse()
    }

    @Test
    fun `cookie content is it's name and value separated by =`() {
        privateAtomicCookie.set(testCookie)
        assertThat(privateAtomicCookie.getCookieContent()).isEqualTo("cookie_name=cookie_value")
    }

    private fun getCookieWithSpecificExpirationTime(expirationTime: Long): AtomicCookie {
        return AtomicCookie(
                expirationTime.toString(),
                testCookie.path,
                testCookie.domain,
                testCookie.name,
                testCookie.value
        )
    }
}