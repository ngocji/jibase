package comx.y.z.kotlinbase.fragment.cache

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.jibase.extensions.collect
import com.jibase.utils.Log
import comx.y.z.kotlinbase.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CacheFragment : Fragment(R.layout.fragment_cache) {
    private val viewModel by viewModels<CacheViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collect(viewModel.getData(10)) {
            Log.d("Data: $it")
        }

        collect(viewModel.getData(10)) {
            Log.d("Data 2: $it")
        }
    }
}