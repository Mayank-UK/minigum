package com.minigum.core.guards

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthEmailException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.minigum.core.exceptions.AppException
import com.minigum.core.exceptions.AuthException
import com.minigum.core.exceptions.NetworkException
import com.minigum.core.exceptions.ServerException

/**
 * Runs a Firebase (Auth / Firestore) call and translates Firebase-specific exceptions into the
 * app's [AppException] hierarchy, rethrowing them. Already-typed [AppException]s pass through
 * untouched.
 *
 * Sibling of [guardedApiCall]: every FirebaseFirestore / FirebaseAuth call should be wrapped so
 * platform types never escape the data layer — Firebase's own exception types are the *source* the
 * guard translates, not a replacement for this pattern.
 *
 * Auth failures map to [AuthException] (whose message is preserved verbatim by
 * [com.minigum.core.mapping.ExceptionMapper]); Firestore failures map to [ServerException];
 * connectivity failures map to [NetworkException].
 */
suspend fun <T> guardedFirebaseCall(block: suspend () -> T): T = try {
    block()
} catch (e: AppException) {
    throw e
} catch (e: FirebaseAuthInvalidCredentialsException) {
    throw AuthException("Invalid email or password.", cause = e)
} catch (e: FirebaseAuthInvalidUserException) {
    throw AuthException("No account found for these credentials.", cause = e)
} catch (e: FirebaseAuthUserCollisionException) {
    throw AuthException("An account already exists with these details.", cause = e)
} catch (e: FirebaseAuthWeakPasswordException) {
    throw AuthException("Password is too weak. Please choose a stronger one.", cause = e)
} catch (e: FirebaseAuthEmailException) {
    throw AuthException("This email address is not valid.", cause = e)
} catch (e: FirebaseFirestoreException) {
    // Firestore surfaces permission/quota/unavailable as distinct codes; map them generically.
    throw ServerException(e.message ?: "Cloud data error.", cause = e)
} catch (e: FirebaseNetworkException) {
    throw NetworkException("Network error talking to Firebase.", cause = e)
} catch (e: Exception) {
    throw ServerException("Unexpected Firebase error.", cause = e)
}
