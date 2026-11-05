package id.taraaport.dreadout2

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0C"))
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val titleTextView = TextView(this).apply {
            text = "DREADOUT 2: TARAA PORT"
            textSize = 24f
            setTextColor(Color.parseColor("#D32F2F"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(titleTextView)

        val subtitleTextView = TextView(this).apply {
            text = "Custom Horror Frontend & Intent Bridge"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        rootLayout.addView(subtitleTextView)

        val btnLaunch = Button(this).apply {
            text = "LUNCURKAN DREADOUT 2"
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Pilih file ROM DreadOut 2 (.nsp)", Toast.LENGTH_SHORT).show()
                openFileSelector()
            }
        }
        
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        rootLayout.addView(btnLaunch, params)

        setContentView(rootLayout)
    }

    private fun openFileSelector() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Pilih ROM .nsp DreadOut 2"), 1001)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                launchEmulatorWithRom(uri)
            }
        }
    }

    private fun launchEmulatorWithRom(uri: Uri) {
        val supportedPackages = listOf(
            "org.sudachi.sudachi_emu",
            "org.citron.citron_emu",
            "org.yuzu.yuzu_emu"
        )

        var launched = false
        for (pkg in supportedPackages) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/octet-stream")
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {}
        }

        if (!launched) {
            try {
                val generalIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(generalIntent, "Buka dengan Emulator Switch"))
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak ada emulator yang dapat membuka file ini!", Toast.LENGTH_LONG).show()
            }
        }
    }
}