package comx.y.z.kotlinbase

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.jibase.utils.FragmentUtils
import com.jibase.utils.Log
import comx.y.z.kotlinbase.fragment.MainFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FragmentUtils.add(
            FragmentUtils.ReplaceOption<MainActivity>()
                .with(this)
                .setContainerId(R.id.flReplace)
                .setFragment(MainFragment())
                .addToBackStack(true)
        )
    }
}