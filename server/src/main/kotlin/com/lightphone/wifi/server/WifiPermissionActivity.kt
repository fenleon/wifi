package com.lightphone.wifi.server

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import com.thelightphone.sdk.shared.LightServiceMethod

/**
 * AOSP runtime-permission host for the SDK's permission-request flow (wired as
 * [com.thelightphone.sdk.server.LightSdkServer.permissionActivity] in
 * [ServerBootstrapProvider]). The SDK's own emulator activity grants via
 * system-uid reflection — impossible for a regular app on the LP3 — so this
 * one asks the standard way and the user grants in the system dialog. Reads
 * the permission the tool requested from
 * [LightServiceMethod.RequestPermissionComponent.PERMISSION_NAME_KEY].
 */
class WifiPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = intent.getStringExtra(
            LightServiceMethod.RequestPermissionComponent.PERMISSION_NAME_KEY
        ) ?: defaultPermission ?: Manifest.permission.CAMERA
        requestPermissions(arrayOf(permission), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        defaultPermission = null
        finish()
    }

    companion object {
        const val REQUEST_CODE = 1

        /**
         * The startServerActivity seam carries no extras, so the screen sets
         * the permission to request here before launching this activity (the
         * SDK permission flow normally provides it via the intent extra).
         */
        @JvmStatic
        var defaultPermission: String? = null
    }
}
