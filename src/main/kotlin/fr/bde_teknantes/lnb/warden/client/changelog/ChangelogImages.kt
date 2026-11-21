package fr.bde_teknantes.lnb.warden.client.changelog

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.client.ClientConfig
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The pictures of the post on screen: downloaded when they come into view, kept as textures, and freed with
 * [release]. Only LNB's own address is fetched, and a picture that can't be loaded stays its alt text.
 */
internal class ChangelogImages {
    sealed interface State {
        data object Loading : State
        data object Failed : State
        data class Ready(val location: ResourceLocation, val width: Int, val height: Int) : State
    }

    private val states = HashMap<String, State>()

    /** Bumped by [release], so an answer to a request made before it is dropped. */
    private var generation = 0

    /** The state of the picture at [url], starting its download the first time it is asked for. */
    fun get(url: String): State {
        states[url]?.let { return it }
        if (!isOurs(url)) {
            LnbWarden.LOGGER.warn("Not loading a changelog picture from another address: {}", url)
            return State.Failed.also { states[url] = it }
        }
        states[url] = State.Loading
        LnbWarden.LOGGER.info("Loading a changelog picture: {}", url)
        val started = generation
        // HTTP/1.1: over plain http, Java first asks to upgrade to HTTP/2, which the site's dev server answers with nothing.
        val request = HttpRequest.newBuilder(URI.create(url)).version(java.net.http.HttpClient.Version.HTTP_1_1).timeout(TIMEOUT).header("Accept", "image/png").GET().build()
        // The body is read by the client's own threads. A picture that says it is too big isn't downloaded at all.
        val handler = HttpResponse.BodyHandler<ByteArray?> { info ->
            if (info.statusCode() != 200 || info.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES) {
                HttpResponse.BodySubscribers.replacing(null)
            } else {
                HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray()) { it as ByteArray? }
            }
        }
        ApiClient.HTTP.sendAsync(request, handler).handle { response, error ->
            when {
                error != null -> {
                    LnbWarden.LOGGER.warn("Couldn't fetch a changelog picture: {}", error.message)
                    null
                }
                response.statusCode() != 200 -> {
                    LnbWarden.LOGGER.warn("Couldn't fetch a changelog picture: HTTP {}", response.statusCode())
                    null
                }
                else -> response.body()?.takeIf { it.size <= MAX_BYTES }
            }
        }.thenAccept { bytes ->
            Minecraft.getInstance().execute { if (started == generation) finish(url, bytes) }
        }
        return State.Loading
    }

    private fun finish(url: String, bytes: ByteArray?) {
        states[url] = runCatching {
            val image = NativeImage.read(ByteArrayInputStream(bytes ?: error("no picture")))
            val location = LnbWarden.id("changelog/image_${counter++}")
            val width = image.width
            val height = image.height
            Minecraft.getInstance().textureManager.register(location, DynamicTexture(image))
            LnbWarden.LOGGER.info("Loaded a changelog picture: {}x{}", width, height)
            State.Ready(location, width, height)
        }.getOrElse {
            LnbWarden.LOGGER.warn("Couldn't load a changelog picture: {}", it.message)
            State.Failed
        }
    }

    /** Frees every texture. The pictures load again if the screen asks for them once more. */
    fun release() {
        generation++
        val textures = Minecraft.getInstance().textureManager
        for (state in states.values) if (state is State.Ready) textures.release(state.location)
        states.clear()
    }

    /** Pictures come from the site the changelog came from, never from an address a post could pick. */
    private fun isOurs(url: String): Boolean = runCatching {
        val uri = URI.create(url)
        uri.scheme in setOf("http", "https") && uri.host != null && uri.host == URI.create(ClientConfig.apiUrl).host
    }.getOrDefault(false)

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        const val MAX_BYTES = 4 * 1024 * 1024
        var counter = 0
    }
}
