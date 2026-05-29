package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Secure Shield", appName)
  }

  @Test
  fun `test search filter matches server names or IPs`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ShieldViewModel(app)

    // Initially search query is empty, and server list shows all of them
    assertTrue(viewModel.uiState.value.serverList.size >= 50)

    // Search for Zurich
    viewModel.updateSearchQuery("Zurich")
    var filtered = viewModel.uiState.value.serverList
    assertEquals(1, filtered.size)
    assertEquals("ch_zurich", filtered[0].id)

    // Search by IP pattern
    viewModel.updateSearchQuery("142.250")
    filtered = viewModel.uiState.value.serverList
    assertTrue(filtered.any { it.id == "us_west" })

    // Clear search
    viewModel.updateSearchQuery("")
    assertTrue(viewModel.uiState.value.serverList.size >= 50)
  }

  @Test
  fun `test favorite caching sticky mechanism`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ShieldViewModel(app)

    val serverId = "us_west"
    assertFalse(viewModel.uiState.value.favoriteServerIds.contains(serverId))

    // Toggle Favorite ON
    viewModel.toggleFavorite(serverId)
    assertTrue(viewModel.uiState.value.favoriteServerIds.contains(serverId))

    // Set Favorites ONLY filter
    viewModel.toggleFavoritesOnly()
    assertEquals(1, viewModel.uiState.value.serverList.size)
    assertEquals(serverId, viewModel.uiState.value.serverList[0].id)

    // Toggle Favorite OFF
    viewModel.toggleFavorite(serverId)
    assertFalse(viewModel.uiState.value.favoriteServerIds.contains(serverId))
    assertEquals(0, viewModel.uiState.value.serverList.size)

    // Turn OFF favorites only filter
    viewModel.toggleFavoritesOnly()
    assertTrue(viewModel.uiState.value.serverList.size >= 50)
  }

  @Test
  fun `test smart routing sorts by lowest latency and capacity`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = ShieldViewModel(app)

    // Turn ON smart sorting
    viewModel.toggleSmartSorting()
    val sortedList = viewModel.uiState.value.serverList

    // Assert that each element's latencyMs is less than or equal to the next
    for (i in 0 until sortedList.size - 1) {
      val cur = sortedList[i]
      val nxt = sortedList[i + 1]
      assertTrue(
        "Expected latencyMs of ${cur.name}(${cur.latencyMs}) <= ${nxt.name}(${nxt.nxtLatencyMessage()})",
        cur.latencyMs <= nxt.latencyMs
      )
    }
  }

  private fun ServerNode.nxtLatencyMessage() = "$latencyMs ms"
}
