package com.aungmyokyaw.librecuts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aungmyokyaw.librecuts.databinding.ActivityProjectImportBinding
import com.aungmyokyaw.librecuts.models.EditRecipe
import com.aungmyokyaw.librecuts.viewmodels.VideoEditingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectImportBinding
    private lateinit var viewModel: VideoEditingViewModel
    private var projectUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[VideoEditingViewModel::class.java]

        projectUri = intent.getParcelableExtra("PROJECT_URI")
        if (projectUri == null) {
            Toast.makeText(this, "No project file selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadProject()
    }

    private fun loadProject() {
        lifecycleScope.launch {
            binding.progressLoading.visibility = android.view.View.VISIBLE

            val recipe = withContext(Dispatchers.IO) {
                viewModel.loadRecipeFromFile(this@ProjectImportActivity, projectUri!!)
            }

            binding.progressLoading.visibility = android.view.View.GONE

            if (recipe != null) {
                viewModel.loadRecipe(recipe)
                Toast.makeText(this@ProjectImportActivity, "Project loaded: ${recipe.projectName}", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@ProjectImportActivity, VideoEditingActivity::class.java).apply {
                    putExtra("VIDEO_URI", recipe.sourceUri)
                    data = recipe.sourceUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@ProjectImportActivity, "Failed to load project", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onBackPressed() {
        finish()
    }
}
