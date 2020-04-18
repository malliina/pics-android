package com.skogberglabs.pics

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.skogberglabs.pics.auth.Google
import com.skogberglabs.pics.backend.UploadService
import kotlinx.android.synthetic.main.main_activity.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    companion object {
        const val requestCodeSignIn = 111
    }

    private lateinit var viewModel: MainActivityViewModel
    val app: PicsApp get() = application as PicsApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)
        val isPrivate = app.settings.isPrivate
        Timber.i("onCreate privately $isPrivate")
        setContentView(R.layout.main_activity)
        setSupportActionBar(appBar)
        if (savedInstanceState == null) {
            appBar.setupWithNavController(navController())
        }
        viewModel.mode.observe(this) { mode ->
            val colors = when (mode) {
                AppMode.Public -> AppColors(
                    statusBar = getColor(R.color.colorLightStatusBar),
                    navigationBar = getColor(R.color.colorLightNavigationBar),
                    background = getColor(R.color.colorLightBackground),
                    actionBar = getColor(R.color.colorLightActionBar)
                )
                AppMode.Private -> AppColors(
                    getColor(R.color.colorDarkStatusBar),
                    getColor(R.color.colorDarkNavigationBar),
                    getColor(R.color.colorDarkBackground),
                    getColor(R.color.colorDarkActionBar)
                )
            }
            window.statusBarColor = colors.statusBar
            // the bottom bar background
            window.navigationBarColor = colors.navigationBar

            supportActionBar?.setBackgroundDrawable(ColorDrawable(colors.actionBar))
            main_view.setBackgroundColor(colors.background)
        }
        viewModel.effectiveUser.observe(this) { user ->
            UploadService.enqueue(applicationContext, user)
        }
        viewModel.signInSilently(applicationContext)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCodeSignIn) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val user = account?.let { a -> Google.readUser(a) }
            Timber.i("Sign in success.")
            app.settings.isPrivate = true
            app.settings.privateEmail = user?.email
            viewModel.updateSignedInUser(user)
        } catch (e: ApiException) {
            val str = CommonStatusCodes.getStatusCodeString(e.statusCode)
            Timber.w(e, "Sign in failed. Code ${e.statusCode}. $str.")
            viewModel.updateSignedInUser(null)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        appBar.setupWithNavController(navController())
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController().navigateUp()
    }

    private fun navController() = findNavController(R.id.nav_host_fragment)
}
