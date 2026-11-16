package fr.familia.app.core.photo

import java.io.IOException

/** The chosen file cannot be read as a picture: another photo is needed, not the network. */
class UnreadablePhotoException(message: String) : IOException(message)

/**
 * The server refused the photo or its record (Storage or Firestore rules): retrying will not help,
 * so the app says so instead of blaming the connection.
 */
class PhotoRefusedException(cause: Throwable) : Exception(cause.message, cause)
