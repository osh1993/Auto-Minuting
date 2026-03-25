package com.autominuting.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * NotebookLM 연동 유틸리티.
 *
 * NotebookLM 앱이 설치되어 있으면 직접 Intent로 회의록을 전달하고,
 * 미설치 시 Custom Tabs로 NotebookLM 웹을 열어 폴백한다.
 */
object NotebookLmHelper {

    /** NotebookLM 앱 패키지명 (Google Play Store 등록 기준) */
    const val NOTEBOOKLM_PACKAGE = "com.google.android.apps.labs.language.tailwind"

    /** NotebookLM 웹 URL */
    const val NOTEBOOKLM_WEB_URL = "https://notebooklm.google.com"

    /**
     * NotebookLM 앱이 설치되어 있는지 확인한다.
     *
     * @param context Android Context
     * @return 설치 여부
     */
    fun isNotebookLmInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(NOTEBOOKLM_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 회의록을 NotebookLM으로 공유한다.
     *
     * 앱이 설치되어 있으면 ACTION_SEND Intent로 직접 전송하고,
     * 미설치이거나 전송 실패 시 Custom Tabs로 NotebookLM 웹을 연다.
     *
     * @param context Android Context
     * @param title 회의록 제목
     * @param content 회의록 내용 (Markdown 텍스트)
     */
    fun shareToNotebookLm(context: Context, title: String, content: String) {
        if (isNotebookLmInstalled(context)) {
            try {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                    type = "text/plain"
                    setPackage(NOTEBOOKLM_PACKAGE)
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // 앱이 설치되어 있지만 공유 Activity가 없는 경우 웹으로 폴백
                openNotebookLmWeb(context)
            }
        } else {
            // 앱 미설치 시 웹으로 폴백
            openNotebookLmWeb(context)
        }
    }

    /**
     * Custom Tabs로 NotebookLM 웹을 연다.
     *
     * Chrome Custom Tabs를 사용하여 인앱 브라우저 경험을 제공한다.
     *
     * @param context Android Context
     */
    fun openNotebookLmWeb(context: Context) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(NOTEBOOKLM_WEB_URL))
    }
}
