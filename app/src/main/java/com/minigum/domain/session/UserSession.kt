package com.minigum.domain.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Holds the player's identity for the current app session.
 *
 * A random display name is assigned once, the first time a [UserSession] is created,
 * and stays stable for the lifetime of the process. Injected anywhere the name is needed
 * (the home drawer, the user screen) so every screen shows the same identity.
 */
@Singleton
class UserSession @Inject constructor() {

    val userName: String = generateName()

    private fun generateName(): String {
        val adjectives = listOf(
            "Brave", "Clever", "Swift", "Jolly", "Mighty", "Happy", "Lucky",
            "Cosmic", "Pixel", "Nimble", "Curious", "Sneaky", "Golden", "Silent", "Royal",
        )
        val animals = listOf(
            "Falcon", "Panda", "Tiger", "Comet", "Otter", "Fox", "Phoenix",
            "Raccoon", "Walrus", "Lynx", "Hedgehog", "Dolphin", "Badger", "Koala", "Hawk",
        )
        return "${adjectives.random(Random)} ${animals.random(Random)}"
    }
}
