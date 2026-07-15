package com.frost.gui

import net.minecraft.client.MinecraftClient

/**
 * The first-launch, two-step onboarding shown the very first time a player joins a server
 * with Frost installed (see [com.frost.config.ModConfig.hasSeenWelcome] and the
 * `ClientPlayConnectionEvents.JOIN` hookup in `Frost.kt`): a welcome/disclaimer message,
 * then a reminder to set up a Hypixel API key.
 */
object OnboardingFlow {
    private const val WELCOME_MESSAGE =
        "Thanks for using Frost. Please understand that this mod is still under heavy " +
            "development, and many flaws or bugs may be present. Please report any bugs or " +
            "feature requests to harryskis on Discord. Thank you!"

    private const val API_KEY_MESSAGE =
        "Frost requires an API Key to function properly. Visit developer.hypixel.net to " +
            "obtain one, then paste it in the \"API Key\" menu within Frost. Remember to " +
            "update it every two days."

    fun start(client: MinecraftClient) {
        client.setScreen(
            OnboardingMessageScreen("Welcome", WELCOME_MESSAGE, "Got it") {
                client.setScreen(
                    OnboardingMessageScreen("API Key", API_KEY_MESSAGE, "Close") {
                        client.setScreen(null)
                    },
                )
            },
        )
    }
}
