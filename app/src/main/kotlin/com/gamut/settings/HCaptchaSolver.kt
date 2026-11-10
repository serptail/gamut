package com.gamut.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import com.hcaptcha.sdk.HCaptcha
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val SITE_KEY = "50b2fe65-b00b-4b9e-ad62-3ba471098be2"

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

suspend fun Activity.solveHCaptcha(): Result<String> = suspendCancellableCoroutine { cont ->
    if (this !is FragmentActivity) {
        cont.resume(Result.failure(IllegalStateException("Activity is not a FragmentActivity")))
        return@suspendCancellableCoroutine
    }
    HCaptcha.getClient(this).verifyWithHCaptcha(SITE_KEY)
        .addOnSuccessListener { r -> if (cont.isActive) cont.resume(Result.success(r.tokenResult)) }
        .addOnFailureListener { e -> if (cont.isActive) cont.resume(Result.failure(e)) }
}
