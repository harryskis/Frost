package com.frost.util

import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.net.URI

/** Opening a URL in the system's default browser - shared between [com.frost.spotify.SpotifyAuth]'s
 * login flow and any GUI "open this link" button, since both need the exact same fallback. */
object BrowserUtils {
    private val logger = LoggerFactory.getLogger("frost.browser")

    /** Tries [Desktop.browse] first, falling back to invoking the OS's own "open a URL"
     * command directly. The fallback matters here specifically: confirmed on a live run
     * that `Desktop.browse()` can throw inside a modded Minecraft JVM even though a browser
     * is perfectly available - LWJGL/GLFW's own window/event handling can interfere with
     * AWT's desktop integration in a way that doesn't happen in a plain JVM. Returns
     * whether either attempt succeeded. */
    fun tryOpenBrowser(url: String): Boolean {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
                return true
            }
        } catch (e: Exception) {
            logger.warn("Desktop.browse() failed, falling back to an OS-level open command", e)
        }
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            val command = when {
                os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
                os.contains("mac") -> listOf("open", url)
                else -> listOf("xdg-open", url)
            }
            ProcessBuilder(command).start()
            true
        } catch (e: Exception) {
            logger.warn("OS-level browser open also failed", e)
            false
        }
    }
}
