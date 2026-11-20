package lensmarket.listing.runtime

import io.smallrye.mutiny.Uni
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Starts a suspend adapter on the subscribing Vert.x thread and exposes it as Mutiny. */
fun <T> suspendToUni(block: suspend () -> T): Uni<T> =
    Uni.createFrom().emitter { emitter ->
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                result.fold(emitter::complete, emitter::fail)
            }
        })
    }
