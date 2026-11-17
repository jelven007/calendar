package com.tiandili.calendar.platform.alarm

interface AlarmSound {
    fun play()
    fun stop()
}

class AlarmSession {
    var jobId: Long? = null
        private set
    private var sound: AlarmSound? = null

    fun start(jobId: Long, sound: AlarmSound) {
        stop()
        this.jobId = jobId
        this.sound = sound
        try {
            sound.play()
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    fun stop(expectedJobId: Long? = null): Boolean {
        if (jobId == null || (expectedJobId != null && expectedJobId != jobId)) return false
        val previous = sound
        sound = null
        jobId = null
        previous?.stop()
        return true
    }
}
