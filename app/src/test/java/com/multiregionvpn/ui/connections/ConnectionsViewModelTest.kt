package com.multiregionvpn.ui.connections

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.MainCoroutineRule
import com.multiregionvpn.data.database.ConnectionEvent
import com.multiregionvpn.data.repository.ConnectionsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionsViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var connectionsRepository: ConnectionsRepository
    private lateinit var viewModel: ConnectionsViewModel

    @Before
    fun setup() {
        connectionsRepository = mockk(relaxed = true)
    }

    private fun createViewModel() {
        viewModel = ConnectionsViewModel(connectionsRepository)
    }

    @Test
    fun `given repository has connection events, when ViewModel is initialized, then uiState is updated with connections`() = runTest {
        // GIVEN: Repository returns some connection events
        val testEvents = listOf(
            ConnectionEvent(
                id = 1,
                timestamp = System.currentTimeMillis(),
                packageName = "com.bbc.iplayer",
                appName = "BBC iPlayer",
                destinationIp = "192.168.1.1",
                destinationPort = 443,
                protocol = "TCP",
                tunnelId = "nordvpn_UK",
                tunnelAlias = "UK VPN"
            ),
            ConnectionEvent(
                id = 2,
                timestamp = System.currentTimeMillis() - 5000,
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                destinationIp = "8.8.8.8",
                destinationPort = 80,
                protocol = "TCP",
                tunnelId = null,
                tunnelAlias = null
            )
        )
        
        every { connectionsRepository.getRecentEvents(any()) } returns flowOf(testEvents)
        
        // WHEN: ViewModel is initialized
        createViewModel()
        
        // Give it a moment to process the flow
        kotlinx.coroutines.delay(100)
        
        // THEN: UI state is updated with connection events
        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.connections).hasSize(2)
        
        // Verify first connection
        val firstConnection = state.connections[0]
        assertThat(firstConnection.appName).isEqualTo("BBC iPlayer")
        assertThat(firstConnection.destination).contains("192.168.1.1")
        assertThat(firstConnection.destination).contains("443")
        assertThat(firstConnection.tunnelAlias).isEqualTo("UK VPN")
        
        // Verify second connection (direct internet)
        val secondConnection = state.connections[1]
        assertThat(secondConnection.appName).isEqualTo("YouTube")
        assertThat(secondConnection.tunnelAlias).isEqualTo("Direct Internet")
    }

    @Test
    fun `given repository has no events, when ViewModel is initialized, then uiState shows empty list`() = runTest {
        // GIVEN: Repository returns empty list
        every { connectionsRepository.getRecentEvents(any()) } returns flowOf(emptyList())
        
        // WHEN: ViewModel is initialized
        createViewModel()
        
        kotlinx.coroutines.delay(100)
        
        // THEN: UI state shows empty list
        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.connections).isEmpty()
    }

    @Test
    fun `given ViewModel is initialized, when refresh is called, then repository is queried again`() = runTest {
        // GIVEN: ViewModel is initialized
        every { connectionsRepository.getRecentEvents(any()) } returns flowOf(emptyList())
        createViewModel()
        kotlinx.coroutines.delay(100)
        
        // WHEN: refresh is called
        viewModel.refresh()
        
        kotlinx.coroutines.delay(100)
        
        // THEN: Repository getRecentEvents is called (at least twice - init + refresh)
        coVerify(atLeast = 2) { connectionsRepository.getRecentEvents(any()) }
    }

    @Test
    fun `given ViewModel is initialized, when clearAll is called, then repository clearAll is invoked`() = runTest {
        // GIVEN: ViewModel is initialized
        every { connectionsRepository.getRecentEvents(any()) } returns flowOf(emptyList())
        coEvery { connectionsRepository.clearAll() } returns Unit
        createViewModel()
        kotlinx.coroutines.delay(100)
        
        // WHEN: clearAll is called
        viewModel.clearAll()
        
        kotlinx.coroutines.delay(100)
        
        // THEN: Repository clearAll is invoked
        coVerify(exactly = 1) { connectionsRepository.clearAll() }
    }

    @Test
    fun `given connection event timestamp, when converted to display model, then timestamp is formatted as HH:mm:ss`() = runTest {
        // GIVEN: Repository returns an event with specific timestamp
        val timestamp = 1700000000000L // Arbitrary timestamp
        val testEvent = ConnectionEvent(
            id = 1,
            timestamp = timestamp,
            packageName = "com.test.app",
            appName = "Test App",
            destinationIp = "1.2.3.4",
            destinationPort = 80,
            protocol = "TCP",
            tunnelId = "test_tunnel",
            tunnelAlias = "Test VPN"
        )
        
        every { connectionsRepository.getRecentEvents(any()) } returns flowOf(listOf(testEvent))
        
        // WHEN: ViewModel is initialized
        createViewModel()
        kotlinx.coroutines.delay(100)
        
        // THEN: Timestamp is formatted
        val state = viewModel.uiState.value
        assertThat(state.connections).hasSize(1)
        
        // Timestamp format should be HH:mm:ss
        val timestampPattern = Regex("\\d{2}:\\d{2}:\\d{2}")
        assertThat(state.connections[0].timestamp).matches(timestampPattern)
    }

    @Test
    fun `given connection event destination, when converted to display model, then destination includes IP and port`() = runTest {
        // GIVEN: Repository returns an event
        val testEvent = ConnectionEvent(
            id = 1,
            timestamp = System.currentTimeMillis(),
            packageName = "com.test.app",
            appName = "Test App",
            destinationIp = "192.168.1.100",
            destinationPort = 8080,
            protocol = "UDP",
            tunnelId = null,
            tunnelAlias = null
        )
        
        every { connectionsRepository.getRecentEvents(any()) } returns flowOf(listOf(testEvent))
        
        // WHEN: ViewModel is initialized
        createViewModel()
        kotlinx.coroutines.delay(100)
        
        // THEN: Destination is formatted as IP:port
        val state = viewModel.uiState.value
        assertThat(state.connections[0].destination).isEqualTo("192.168.1.100:8080")
    }
}
