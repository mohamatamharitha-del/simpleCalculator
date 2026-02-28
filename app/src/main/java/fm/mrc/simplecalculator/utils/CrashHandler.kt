package fm.mrc.simplecalculator.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import fm.mrc.simplecalculator.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        fun init(context: Context) {
            val handler = CrashHandler(context)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            
            Log.e(TAG, "Uncaught Exception: $stackTrace")

            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra("error_message", throwable.localizedMessage ?: "Unknown error")
                putExtra("stack_trace", stackTrace.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

            // Kill the current process
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        } catch (e: Exception) {
            Log.e(TAG, "Error in CrashHandler", e)
        }
    }
}
