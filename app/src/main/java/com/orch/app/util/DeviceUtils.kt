package com.orch.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import java.io.File

/**
 * Utility functions for checking network connectivity and storage space.
 */
object DeviceUtils {

    /**
     * Check if the device has an active internet connection.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Check if internet is available over WiFi (not metered).
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
               !connectivityManager.isActiveNetworkMetered
    }

    /**
     * Check if connection is metered (mobile data).
     */
    fun isMeteredConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.isActiveNetworkMetered
    }

    /**
     * Get available storage space in bytes for the given directory.
     */
    fun getAvailableStorageBytes(directory: File?): Long {
        return try {
            val path = directory?.absolutePath ?: return 0L
            val stat = StatFs(path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if there's enough storage space for a download.
     * @param directory The directory where the file will be stored
     * @param requiredBytes The number of bytes required
     * @param bufferPercent Extra buffer percentage (default 10%)
     */
    fun hasEnoughStorage(directory: File?, requiredBytes: Long, bufferPercent: Int = 10): Boolean {
        val available = getAvailableStorageBytes(directory)
        val required = requiredBytes + (requiredBytes * bufferPercent / 100)
        return available >= required
    }

    /**
     * Format bytes to human-readable string (e.g., "1.5 GB").
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024L -> String.format("%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
}
