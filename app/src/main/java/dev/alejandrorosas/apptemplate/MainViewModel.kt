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

    fun onServiceConnected(mService: StreamService) {
        viewState.postValue(
            ViewState(
                serviceButtonText = R.string.button_stop_service,
                streamButtonText = if (mService.isStreaming) R.string.button_stop_stream else R.string.button_start_stream,
            )
        )
    }

    fun onServiceDisconnected() {
        viewState.postValue(
            ViewState(
                serviceButtonText = R.string.button_start_service,
                streamButtonText = null,
            )
        )
    }

    fun onStopService() {
        onServiceDisconnected()
    }

    fun onStreamControlButtonClick() {
        withService {
            if (it.isStreaming) {
                it.stopStream()
            } else {
                val endpoint = sharedPreferences.getString("endpoint", null)

                if (endpoint.isNullOrBlank()) {
//                    Toast.makeText(this, R.string.toast_missing_stream_url, Toast.LENGTH_LONG).show()
                    return@withService
                }

                val streamStarted = it.startStreamRtp(endpoint)
                viewState.postValue(
                    viewState.value!!.copy(
                        streamButtonText = if (streamStarted) R.string.button_stop_stream else R.string.button_start_stream
                    )
                )
            }
        }
    }

    private fun withService(block: (StreamService) -> Unit) {
        serviceLiveData.value = block
    }

    data class ViewState(
        val serviceButtonText: Int = R.string.button_start_service,
        val streamButtonText: Int? = null,
    )
}
