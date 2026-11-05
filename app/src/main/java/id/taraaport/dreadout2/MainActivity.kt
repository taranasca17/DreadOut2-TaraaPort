package id.taraaport.dreadout2

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Membuat Layout Utama Fullscreen dengan tema Horor Gelap
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0C")) // Hitam pekat horor
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        // Judul Aplikasi / Game
        val titleTextView = TextView(this).apply {
            text = "DREADOUT 2: TARAA PORT"
            textSize = 24f
            setTextColor(Color.parseColor("#D32F2F")) // Merah darah
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(titleTextView)

        // Subtitle / Status
        val subtitleTextView = TextView(this).apply {
            text = "Custom Horror Frontend & Control Overlay"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        rootLayout.addView(subtitleTextView)

        // Tombol Aksi Utama: Luncurkan Game
        val btnLaunch = Button(this).apply {
            text = "LUNCURKAN DREADOUT 2"
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Mempersiapkan atmosfer game...", Toast.LENGTH_SHORT).show()
                // Mencari file ROM umum atau memicu pemilih file eksternal
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
            startActivityForResult(Intent.createChooser(intent, "Pilih File ROM / NSP DreadOut 2"), 1001)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka file picker: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                Toast.makeText(this, "File terpilih, meluncurkan...", Toast.LENGTH_SHORT).show()
                // Mengirim intent ke emulator atau menjalankan payload
            }
        }
    }
}