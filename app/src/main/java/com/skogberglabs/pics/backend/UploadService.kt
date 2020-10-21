package com.skogberglabs.pics.backend

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.skogberglabs.pics.backend.OkClient.Companion.Accept
import com.skogberglabs.pics.backend.OkClient.Companion.Authorization
import com.skogberglabs.pics.backend.PicsOkClient.Companion.CsrfHeaderName
import com.skogberglabs.pics.backend.PicsOkClient.Companion.CsrfTokenNoCheck
import com.skogberglabs.pics.ui.camera.SimpleCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class UploadService : JobIntentService() {
    companion object {
        const val EmailKey = "email"
        const val TokenKey = "token"
        const val XClientPic = "X-Client-Pic"

        // "A unique job ID for scheduling; must be the same value for all work enqueued for the same class."
        private const val uploadJobsId = 12345

        fun enqueue(context: Context, user: UserInfo?) {
            val intent = Intent()
            user?.let { u ->
                intent.putExtra(EmailKey, u.email.value)
                intent.putExtra(TokenKey, u.idToken.value)
            }
            enqueue(context, intent)
        }

        private fun enqueue(context: Context, intent: Intent) {
            enqueueWork(context, UploadService::class.java, uploadJobsId, intent)
        }

        private val backgroundScope = CoroutineScope(Dispatchers.IO)
        val uploadUrl = FullUrl.https("pics.malliina.com", "/pics")
    }

    val http = OkClient(TokenSource.empty)

    private val cameraFiles: SimpleCamera by lazy { SimpleCamera(applicationContext) }

    override fun onHandleWork(intent: Intent) {
        val user = intent.getStringExtra(EmailKey)?.let { e ->
            intent.getStringExtra(TokenKey)?.let { t ->
                UserInfo(Email(e), IdToken(t))
            }
        }
        backgroundScope.launch {
            recallTooOldUploadingFiles(user)
            uploadFromStagingOldestFirst(user)
        }
    }

    private fun headers(token: IdToken?): Map<String, String> {
        val acceptPair = Accept to PicsOkClient.PicsVersion10
        return if (token != null) mapOf(
            Authorization to "Bearer $token",
            acceptPair
        ) else mapOf(acceptPair)
    }

    private fun recallTooOldUploadingFiles(user: UserInfo?) {
        val files = cameraFiles.uploadingDirectory(user?.email).listFiles() ?: emptyArray()
        files.filter { f ->
            val ageMillis = System.currentTimeMillis() - f.lastModified()
            val sixHours = 6*60*60*1000
            ageMillis > sixHours
        }.forEach { file ->
            val dest = cameraFiles.stagingDirectory(user?.email).resolve(file.name)
            file.renameTo(dest)
        }
    }

    private suspend fun uploadFromStagingOldestFirst(user: UserInfo?) {
        val email = user?.email
        val files = cameraFiles.stagingDirectory(email).listFiles() ?: emptyArray()
        Timber.i("Found ${files.size} files in staging directory for ${email?.value ?: "anonymous"}.")
        files.minByOrNull { f -> f.lastModified() }?.let { oldestStaging ->
            if (oldestStaging.length() > 0) {
                val uploadingDir = cameraFiles.uploadingDirectory(email)
                uploadingDir.mkdirs()
                val fileToUpload = uploadingDir.resolve(oldestStaging.name)
                val success = oldestStaging.renameTo(fileToUpload)
                if (success) {
                    val authHeaders = headers(user?.idToken)
                    val headers = authHeaders + mapOf(
                        XClientPic to oldestStaging.name,
                        CsrfHeaderName to CsrfTokenNoCheck
                    )
                    val isUploadSuccess = try {
                        val response = http.postFile(fileToUpload, uploadUrl, headers)
                        if (response.isSuccessful) {
                            fileToUpload.delete()
                        } else {
                            Timber.w("Non-OK status ${response.code} uploading '$fileToUpload' to '$uploadUrl'.")
                            val isMoveBackSuccess = fileToUpload.renameTo(oldestStaging)
                            if (isMoveBackSuccess) {
                                Timber.i("Moved '$fileToUpload' back to '$oldestStaging'.")
                            } else {
                                Timber.w("Failed to move '$fileToUpload' to '$oldestStaging'.")
                            }
                            false
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to upload '$fileToUpload' to '$uploadUrl'.")
                        fileToUpload.renameTo(oldestStaging)
                        false
                    }
                    if (isUploadSuccess) {
                        uploadFromStagingOldestFirst(user)
                    }
                } else {
                    Timber.e("Failed to move file from '$oldestStaging' to '$fileToUpload'.")
                }
            } else {
                // deletes files in staging dir of size 0 bytes
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
