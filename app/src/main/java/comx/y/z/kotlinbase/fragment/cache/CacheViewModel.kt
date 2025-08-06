package comx.y.z.kotlinbase.fragment.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jibase.flow.cache.DataCacheManager
import com.jibase.utils.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class CacheViewModel @Inject constructor() : ViewModel() {
    private val cacheManager = DataCacheManager(viewModelScope)

    fun getData(i: Int): Flow<Int> {
        return cacheManager.getData("x", i, 0) { p->
            flowOf(p)
        }.apply {
            Log.d("getData: $this")
        }
    }
}