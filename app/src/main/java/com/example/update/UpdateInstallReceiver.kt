package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Install failures reported by the system installer, observed by [UpdateViewModel]. */
object UpdateEvents {
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failures: SharedFlow<String> = _failures.asSharedFlow()

    fun reportFailure(message: String) {
        _failures.tryEmit(message)
    }
}

/** Receives the result of a [PackageInstaller] session started by [AppUpdater.install]. */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system needs the user to confirm; it supplies the confirmation screen.
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            // On success Android replaces this app and closes its process.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateEvents.reportFailure("The update was cancelled.")
            else -> UpdateEvents.reportFailure(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update could not be installed."
            )
        }
    }
}
