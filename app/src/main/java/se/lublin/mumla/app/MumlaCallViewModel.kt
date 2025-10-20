/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.lublin.mumla.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IUser
import se.lublin.humla.model.Server
import se.lublin.humla.protobuf.Mumble
import se.lublin.humla.util.HumlaException

sealed class ServiceAction {
    object DISCONNECT : ServiceAction()

    object REGISTER : ServiceAction()
    data class CONNECT(val server: Server) : ServiceAction()

    data class CREATE_CHANNEL(val name: String) : ServiceAction()

    data class JOIN_CHANNEL(val channel: IChannel) : ServiceAction()
}

sealed class ServiceState(val channelName: String, val retryBackOff: Int = 3) {
    data class DISCONNECTED(
        private val _channelName: String,
        val reconnect: Boolean,
        private val _retryBackOff: Int = 3
    ) : ServiceState(_channelName, _retryBackOff)

    data class CONNECTING(
        private val _channelName: String,
        private val _retryBackOff: Int = 3
    ) : ServiceState(_channelName, _retryBackOff)

    data class CONNECTED(
        private val _channelName: String,
        val registered: Boolean = false
    ) : ServiceState(_channelName, 3)

    data class DISCONNECTING(
        private val _channelName: String,
        private val _retryBackOff: Int = 3
    ) : ServiceState(_channelName, _retryBackOff)

    data class JOINED(
        private val _channelName: String,
    ) : ServiceState(_channelName, 3)
}

@KoinViewModel
class MumlaCallViewModel(
    application: Application,
) : BaseHumlaViewModel(application) {

    private val _actionSharedFlow: MutableSharedFlow<ServiceAction> = MutableSharedFlow()
    val actionSharedFlow = _actionSharedFlow.asSharedFlow()

    private val _serverStateFlow: MutableStateFlow<ServiceState> =
        MutableStateFlow(ServiceState.DISCONNECTED("", false))
    val serverStateFlow: StateFlow<ServiceState> = _serverStateFlow.asStateFlow()


    fun connectServer(channelName: String) {
        val server = Server(
            -1,
            "",
            "uat-voip.1111job.app",
            64738,
            "UserR_${Random.nextInt(1000)}",
            "52@11118888"
        )
        viewModelScope.launch {
            _actionSharedFlow.emit(ServiceAction.CONNECT(server))
            val backOffTime =
                (_serverStateFlow.value as? ServiceState.DISCONNECTED)?.retryBackOff ?: 3
            _serverStateFlow.emit(ServiceState.CONNECTING(channelName, backOffTime))
        }
    }

    fun createChannel(name: String) {
        Log.d("MumlaCallViewModel", "createChannel $name")
        viewModelScope.launch {
            _actionSharedFlow.emit(ServiceAction.CREATE_CHANNEL(name))
        }
    }

    fun disconnectServer() {
        viewModelScope.launch {
            val channelName = _serverStateFlow.value.channelName
            _actionSharedFlow.emit(ServiceAction.DISCONNECT)
            _serverStateFlow.emit(ServiceState.DISCONNECTING(channelName))
        }
    }


    override fun onDisconnected(e: HumlaException?) {
        super.onDisconnected(e)
        Log.d("MumlaCallViewModel", "onDisconnected")
        viewModelScope.launch {
            val backOffTime =
                (_serverStateFlow.value).retryBackOff

            _serverStateFlow.emit(
                ServiceState.DISCONNECTED(
                    _serverStateFlow.value.channelName,
                    reconnect = _serverStateFlow.value !is ServiceState.DISCONNECTING,
                    _retryBackOff = backOffTime * 2
                )
            )
        }
    }

    override fun onUserRemoved(user: IUser, reason: String?) {
        super.onUserRemoved(user, reason)
        Log.d("MumlaCallViewModel", "onUserRemoved ${user.name}, $reason")
    }

    override fun onConnected(msg: Mumble.ServerSync) {
        super.onConnected(msg)
        Log.d("MumlaCallViewModel", "Connected, Welcome: ${msg.welcomeText}")
        viewModelScope.launch {
            val channelName =
                (_serverStateFlow.value as? ServiceState.CONNECTING)?.channelName ?: return@launch

            _serverStateFlow.emit(
                ServiceState.CONNECTED(channelName)
            )
        }
    }

    override fun onUserStateUpdated(user: IUser) {
        super.onUserStateUpdated(user)
        Log.d("MumlaCallViewModel", "User ${user.name}")
    }

    override fun onChannelAdded(channel: IChannel) {
        super.onChannelAdded(channel)
        viewModelScope.launch {
            val channelName =
                (_serverStateFlow.value as? ServiceState.CONNECTED)?.channelName ?: return@launch
            _actionSharedFlow.emit(ServiceAction.JOIN_CHANNEL(channel))
        }
        Log.d(
            "MumlaCallViewModel",
            "Channel Created ${channel.name}, ${channel.id}, ${channel.isTemporary}"
        )
    }

    override fun onUserJoinedChannel(
        user: IUser,
        newChannel: IChannel,
        oldChannel: IChannel
    ) {
        super.onUserJoinedChannel(user, newChannel, oldChannel)
        viewModelScope.launch {
            _serverStateFlow.value = ServiceState.JOINED(newChannel.name)
        }
    }

    override fun onPermissionDenied(reason: String?) {
        super.onPermissionDenied(reason)
        //Check if any certificate issue here
        Log.e("MumlaCallViewModel", "onPermissionDenied $reason")
    }

    override fun onLogError(message: String) {
        super.onLogError(message)
        Log.e("MumlaCallViewModel", message)
    }

    override fun onCleared() {
        super.onCleared()
        disconnectServer()
    }
}