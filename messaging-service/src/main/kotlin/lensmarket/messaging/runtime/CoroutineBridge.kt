package lensmarket.messaging.runtime

import io.smallrye.mutiny.Uni
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Startet einen suspendierenden Adapter auf dem abonnierenden Vert.x-Thread. */
fun <T> suspendToUni(block: suspend () -> T): Uni<T> =
    Uni.createFrom().emitter { emitter ->
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                result.fold(emitter::complete, emitter::fail)
            }
        })
    }
