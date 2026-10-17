package com.javes.app.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class HttpResponse(val code: Int, val body: String)

/**
 * POST com corpo JSON usando apenas a API nativa do Android.
 * Cancelar a corrotina fecha a conexão de verdade.
 * Nunca registra URL, cabeçalhos ou corpo em logs (os cabeçalhos podem conter a chave de API).
 */
internal suspend fun postJson(
    url: String,
    headers: Map<String, String>,
    body: String,
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 45_000
): HttpResponse = suspendCancellableCoroutine { cont ->
    val connection = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (e: Exception) {
        cont.resumeWithException(e)
        return@suspendCancellableCoroutine
    }
    cont.invokeOnCancellation { connection.disconnect() }

    thread(isDaemon = true, name = "javes-http") {
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (cont.isActive) cont.resume(HttpResponse(code, text))
        } catch (e: Throwable) {
            if (cont.isActive) cont.resumeWithException(e)
        } finally {
            connection.disconnect()
        }
    }
}
