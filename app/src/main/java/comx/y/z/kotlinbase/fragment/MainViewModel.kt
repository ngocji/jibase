package comx.y.z.kotlinbase.fragment

import androidx.lifecycle.ViewModel
import com.jibase.pref.DataStoreHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStoreHelper: DataStoreHelper
) : ViewModel() {
    var count = 1
}