package com.tiandili.calendar.platform.alarm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlarmSessionTest {
    @Test
    fun `stopping and replacing the alarm releases sound once and ignores unrelated cancellation`() {
        val first = FakeSound()
        val second = FakeSound()
        val session = AlarmSession()
        session.start(7, first)
        assertThat(first.played).isEqualTo(1)
        session.start(8, second)
        assertThat(first.stopped).isEqualTo(1)
        assertThat(session.stop(7)).isFalse()
        assertThat(second.stopped).isEqualTo(0)
        assertThat(session.stop(8)).isTrue()
        assertThat(session.stop(8)).isFalse()
        assertThat(second.stopped).isEqualTo(1)
    }
}

private class FakeSound : AlarmSound {
    var played = 0
    var stopped = 0
    override fun play() { played++ }
    override fun stop() { stopped++ }
}
