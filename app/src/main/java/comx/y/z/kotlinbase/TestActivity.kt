package comx.y.z.kotlinbase

import android.os.Bundle
import com.jibase.anotation.ViewInflate
import com.jibase.ui.BaseViewModel
import com.jibase.ui.base.BaseActivity
import com.jibase.utils.Log
import com.jibase.utils.SharePreferencesUtils

@ViewInflate(layout = R.layout.activity_main, viewModel = BaseViewModel::class)
class TestActivity : BaseActivity<BaseViewModel>() {

    override fun onViewReady(savedInstanceState: Bundle?) {
        SharePreferencesUtils.putPref("data", Text("LALALA"))

        val data = SharePreferencesUtils.getPref("data", Text("default"))
        Log.e("OnData: ${data.data}")
    }


    class Text(val data: String)
}