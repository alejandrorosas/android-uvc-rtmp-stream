package dev.alejandrorosas.apptemplate

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alejandrorosas.core.livedata.SingleLiveEvent
import dev.alejandrorosas.streamlib.StreamService
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    private val serviceLiveData = SingleLiveEvent<(StreamService) -> Unit>()
    val serviceLiveEvent: LiveData<(StreamService) -> Unit> get() = serviceLiveData

    private var viewState = MutableLiveData(ViewState())

    fun getViewState(): LiveData<ViewState> = viewState

    fun onStreamControlButtonClick() {
        withService {
            if (it.isStreaming) {
                it.stopStream(true)

                viewState.postValue(viewState.value!!.copy(streamButtonText = R.string.button_start_stream))
            } else {
                val endpoint = sharedPreferences.getString("endpoint", null)

                if (endpoint.isNullOrBlank()) {
//                    Toast.makeText(this, R.string.toast_missing_stream_url, Toast.LENGTH_LONG).show()
                    return@withService
                }

                it.startStreamRtp(endpoint)
                viewState.postValue(viewState.value!!.copy(streamButtonText = R.string.button_stop_stream))
            }
        }
    }

    private fun withService(block: (StreamService) -> Unit) {
        serviceLiveData.value = block
    }

    data class ViewState(
        val streamButtonText: Int = R.string.button_start_stream,
    )
}
