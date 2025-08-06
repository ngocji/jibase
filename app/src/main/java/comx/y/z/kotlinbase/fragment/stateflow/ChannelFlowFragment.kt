package comx.y.z.kotlinbase.fragment.stateflow

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.jibase.extensions.collect
import com.jibase.extensions.viewBinding
import comx.y.z.kotlinbase.R
import comx.y.z.kotlinbase.databinding.FragmentChannelFlowBinding
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random

class ChannelFlowFragment : Fragment(R.layout.fragment_channel_flow) {
    private val binding by viewBinding(FragmentChannelFlowBinding::bind)

    val channel = Channel<Int>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collect(channel) { count ->
            binding.tv.setText(count.toString())
        }

        binding.btn.setOnClickListener {
            channel.trySend(Random.nextInt())
        }
    }
}