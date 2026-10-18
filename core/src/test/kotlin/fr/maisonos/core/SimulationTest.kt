package fr.maisonos.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SimulationTest {
    @Test fun repeatedReconnectsCannotExhaustCameraLoginBudget() {
        val limiter = LoginLimiter()
        assertTrue(limiter.allow("camera1", 0))
        assertFalse(limiter.allow("camera1", 1000))
        assertTrue(limiter.allow("camera1", 5000))
        assertTrue(limiter.allow("camera1", 10000))
        assertFalse(limiter.allow("camera1", 15000))
        assertTrue(limiter.allow("camera2", 15000))
        assertTrue(limiter.allow("camera1", 60000))
    }
    @Test fun simulationPublishesAnExplicitlySimulatedEvent() = runBlocking {
        val connector = SimulationConnector()
        val next = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(1000) { connector.events.first() } }
        connector.mailboxOpened()
        val event = next.await()
        assertTrue(event.simulated)
        assertEquals("opening", event.type)
        assertEquals("simulation-mailbox", event.deviceId)
    }
}
