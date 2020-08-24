package comx.y.z.kotlinbase

import android.os.Bundle
import com.jibase.anotation.ViewInflate
import com.jibase.ui.base.SimpleBaseFragment
import com.jibase.utils.ToastUtils

@ViewInflate(layout = R.layout.fragment_test_1, enableBackPressed = true)
class TestFragment : SimpleBaseFragment(){
    override fun onViewReady(savedInstanceState: Bundle?) {

    }

    override fun onBackPressed() {
        ToastUtils.showText(requireContext(), "Back")
    }
}