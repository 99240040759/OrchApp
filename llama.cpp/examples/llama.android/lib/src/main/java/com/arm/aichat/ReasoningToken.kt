package com.arm.aichat

/**
 * A single token emitted during reasoning-aware generation.
 *
 * [Thinking] tokens come from inside the model's <think>…</think> block.
 * [Content]  tokens are the final visible answer.
 * [Done]     signals the stream has ended (EOS or cancel).
 */
sealed class ReasoningToken {
    data class Thinking(val text: String) : ReasoningToken()
    data class Content(val text: String)  : ReasoningToken()
    object Done : ReasoningToken()
}

/** Byte value used as a sentinel prefix to mark THINKING tokens from native code */
const val THINKING_SENTINEL = '\u0001'
