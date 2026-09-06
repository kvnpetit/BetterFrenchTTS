package io.github.kvnpetit.betterfrenchtts

/** Serializes native controls with synthesis completion, including QUEUE_ADD across calls. */
internal class NativePlayback(
    private val submit: (NativeSpeechStep, (SpeechResult) -> Unit) -> SpeechResult,
    private val post: (() -> Unit) -> Unit
) {
    private data class Job(val steps: List<NativeSpeechStep>, val done: (SpeechResult) -> Unit, var index: Int = 0)
    private val jobs = ArrayDeque<Job>()

    @Synchronized fun enqueue(steps: List<NativeSpeechStep>, done: (SpeechResult) -> Unit): SpeechResult {
        if (steps.isEmpty()) { done(SpeechResult.Success); return SpeechResult.Success }
        val job = Job(steps, done)
        jobs.addLast(job)
        return if (jobs.size == 1) dispatch(job) else SpeechResult.Success
    }

    private fun dispatch(job: Job): SpeechResult {
        val result = submit(job.steps[job.index]) { result -> post { complete(job, result) } }
        if (result != SpeechResult.Success) complete(job, result)
        return result
    }

    @Synchronized private fun complete(job: Job, result: SpeechResult) {
        if (jobs.firstOrNull() !== job) return
        if (result == SpeechResult.Success && ++job.index < job.steps.size) { dispatch(job); return }
        jobs.removeFirst()
        val next = jobs.firstOrNull()
        job.done(result)
        // The callback may enqueue the next application queue item itself.
        if (next != null) post { synchronized(this) { if (jobs.firstOrNull() === next) dispatch(next) } }
    }

    @Synchronized fun cancel() {
        val cancelled = jobs.toList()
        jobs.clear()
        cancelled.forEach { it.done(SpeechResult.Error("Playback interrupted")) }
    }
}
