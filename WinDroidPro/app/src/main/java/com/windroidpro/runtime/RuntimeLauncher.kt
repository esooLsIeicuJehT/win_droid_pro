package com.windroidpro.runtime

import android.content.Context
import android.content.Intent
import com.winlator.XServerDisplayActivity
import java.io.File

object RuntimeLauncher {
    fun desktop(context: Context, runtimeId: Int) {
        context.startActivity(Intent(context, XServerDisplayActivity::class.java).putExtra("container_id", runtimeId))
    }

    fun executable(context: Context, runtimeId: Int, file: File) {
        context.startActivity(Intent(context, XServerDisplayActivity::class.java)
            .putExtra("container_id", runtimeId).putExtra("exec_path", file.absolutePath))
    }

    fun settings(context: Context, menuItem: Int = com.winlator.R.id.menu_item_settings) {
        context.startActivity(Intent(context, com.winlator.MainActivity::class.java)
            .putExtra("selected_menu_item_id", menuItem))
    }
}
