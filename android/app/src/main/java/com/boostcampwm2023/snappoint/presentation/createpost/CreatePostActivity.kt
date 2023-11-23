package com.boostcampwm2023.snappoint.presentation.createpost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.boostcampwm2023.snappoint.R
import com.boostcampwm2023.snappoint.databinding.ActivityCreatePostBinding
import com.boostcampwm2023.snappoint.presentation.base.BaseActivity
import com.boostcampwm2023.snappoint.presentation.markerpointselector.MarkerPointSelectorActivity
import com.boostcampwm2023.snappoint.presentation.model.PositionState
import com.boostcampwm2023.snappoint.presentation.util.MetadataUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreatePostActivity : BaseActivity<ActivityCreatePostBinding>(R.layout.activity_create_post) {

    private val viewModel: CreatePostViewModel by viewModels()

    private val imagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.containsValue(false).not()) {
                launchImageSelectionLauncher()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.message_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val imageSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri = result.data?.data ?: return@registerForActivityResult
                val inputStream = this.contentResolver.openInputStream(imageUri)
                    ?: return@registerForActivityResult
                val position = MetadataUtil.extractLocationFromInputStream(inputStream)
                    .getOrDefault(PositionState(0.0, 0.0))
                viewModel.addImageBlock(imageUri, position)

                startMapActivityAndFindAddress(viewModel.uiState.value.postBlocks.lastIndex, position)
            }
        }

    private val addressSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let {
                    viewModel.setAddressAndPosition(
                        index = it.getIntExtra("index", 0),
                        address = it.getStringExtra("address") ?: "",
                        position = PositionState(
                            it.getDoubleExtra("longitude", 0.0),
                            it.getDoubleExtra("latitude", 0.0)
                        )
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initBinding()
        collectViewModelData()
    }

    private fun initBinding() {
        with(binding) {
            vm = viewModel
        }
    }

    private fun collectViewModelData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is CreatePostEvent.ShowMessage -> {
                            showToastMessage(event.resId)
                        }

                        is CreatePostEvent.SelectImageFromLocal -> {
                            selectImage()
                        }

                        is CreatePostEvent.NavigatePrev -> {
                            finish()
                        }

                        is CreatePostEvent.FindAddress -> {
                            startMapActivityAndFindAddress(event.index, event.position)
                        }
                    }
                }
            }

        }
    }

    private fun showToastMessage(resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_LONG).show()
    }

    private fun selectImage() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )

            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        getImageWithPermissionCheck(permissions)
    }

    private fun launchImageSelectionLauncher() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.setDataAndType(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "image/*"
        )
        imageSelectionLauncher.launch(intent)
    }

    private fun getImageWithPermissionCheck(permissions: Array<String>) {
        val permissionCheck = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) == PackageManager.PERMISSION_DENIED
        }

        if (permissionCheck.isNotEmpty()) {
            if (permissionCheck.any { shouldShowRequestPermissionRationale(it) }) {
                Toast.makeText(
                    this,
                    getString(R.string.message_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        imagePermissionLauncher.launch(permissions)
    }


    private fun startMapActivityAndFindAddress(index: Int, position: PositionState) {
        val intent = Intent(this, MarkerPointSelectorActivity::class.java)
        intent.putExtra("index", index)
        intent.putExtra("position", position.asDoubleArray())
        addressSelectionLauncher.launch(intent)
    }
}