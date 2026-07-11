package com.minigum.domain.session

import javax.inject.Qualifier
import kotlin.random.Random

/**
 * Marks the singleton guest display name shown while the user is signed out, so it can be injected
 * (as a plain [String]) into both [com.minigum.data.repositoryimplementations.AuthRepositoryImpl]
 * (for labelling score rows when a profile has no name) and [UserSession] (for the drawer).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GuestDisplayName

private val adjectives = listOf(
    "Brave", "Clever", "Swift", "Jolly", "Mighty", "Happy", "Lucky",
    "Cosmic", "Pixel", "Nimble", "Curious", "Sneaky", "Golden", "Silent", "Royal",
)

private val animals = listOf(
    "Falcon", "Panda", "Tiger", "Comet", "Otter", "Fox", "Phoenix",
    "Raccoon", "Walrus", "Lynx", "Hedgehog", "Dolphin", "Badger", "Koala", "Hawk",
)

/** Generates a fun, stable-for-app-lifetime display name for guests (signed-out users). */
fun randomGuestName(): String = "${adjectives.random(Random)} ${animals.random(Random)}"
