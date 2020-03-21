package com.skogberglabs.pics.backend

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.skogberglabs.pics.ui.camera.SimpleCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Exception

class UploadService : JobIntentService() {
    companion object {
        const val EmailKey = "email"
        const val TokenKey = "token"
        const val XClientPic = "X-Client-Pic"
        const val CsrfHeaderName = "Csrf-Token"
        const val CsrfTokenNoCheck = "nocheck"

        // "A unique job ID for scheduling; must be the same value for all work enqueued for the same class."
        private const val uploadJobsId = 12345

        fun enqueue(context: Context, user: UserInfo?) {
            user?.let { u ->
                val intent = Intent().apply {
                    putExtra(EmailKey, u.email.value)
                    putExtra(TokenKey, u.idToken.value)
                }
                enqueue(context, intent)
            }
        }

        fun enqueue(context: Context, intent: Intent) {
            enqueueWork(context, UploadService::class.java, uploadJobsId, intent)
        }

        private val backgroundScope = CoroutineScope(Dispatchers.IO)
        val uploadUrl = FullUrl.https("pics.malliina.com", "/pics")
    }

    private val cameraFiles: SimpleCamera by lazy { SimpleCamera(applicationContext) }

    override fun onHandleWork(intent: Intent) {
        val user = intent.getStringExtra(EmailKey)?.let { e ->
            intent.getStringExtra(TokenKey)?.let { t ->
                UserInfo(Email(e), IdToken(t))
            }
        }
        backgroundScope.launch { uploadFromStagingOldestFirst(user) }
    }

    private suspend fun uploadFromStagingOldestFirst(user: UserInfo?) {
        val email = user?.email
        val files = cameraFiles.stagingDirectory(email).listFiles() ?: emptyArray()
        Timber.i("Found ${files.size} files in staging directory for ${email?.value ?: "anonymous"}.")
        files.minBy { f -> f.lastModified() }?.let { oldestStaging ->
            if (oldestStaging.length() > 0) {
                val uploadingDir = cameraFiles.uploadingDirectory(email)
                uploadingDir.mkdirs()
                val uploadingFile = uploadingDir.resolve(oldestStaging.name)
                val success = oldestStaging.renameTo(uploadingFile)
                if (success) {
                    val authHeaders = HttpClient.headers(user?.idToken)
                    val headers = authHeaders + mapOf(
                        XClientPic to oldestStaging.name,
                        CsrfHeaderName to CsrfTokenNoCheck
                    )
                    val isUploadSuccess = try {
                        val response = OkClient.default.postFile(uploadingFile, uploadUrl, headers)
                        if (response.isSuccessful) {
                            uploadingFile.delete()
                        } else {
                            throw Exception("Non-OK upload status ${response.code}.")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to upload '$uploadingFile' to '$uploadUrl'.")
                        uploadingFile.renameTo(oldestStaging)
                        false
                    }
                    if (isUploadSuccess) {
                        uploadFromStagingOldestFirst(user)
                    }
                } else {
                    Timber.e("Failed to move file from '$oldestStaging' to '$uploadingFile'.")
                }
            } else {
                val wasDeleted = oldestStaging.delete()
                val msg =
                    if (wasDeleted) "Deleted empty file '$oldestStaging'."
                    else "Failed to delete empty file '$oldestStaging'."
                Timber.i(msg)
                if (wasDeleted) {
                    uploadFromStagingOldestFirst(user)
                }
            }
        }
    }
}
