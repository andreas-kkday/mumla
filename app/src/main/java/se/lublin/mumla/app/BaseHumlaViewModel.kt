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
import androidx.lifecycle.AndroidViewModel
import se.lublin.humla.model.IChannel
import se.lublin.humla.model.IMessage
import se.lublin.humla.model.IUser
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.IHumlaObserver
import se.lublin.humla.util.VoiceTargetMode
import java.security.cert.X509Certificate

abstract class BaseHumlaViewModel(application: Application) : AndroidViewModel(application), IHumlaObserver {

    override fun onConnected() {
    }

    override fun onConnecting() {
    }

    override fun onDisconnected(e: HumlaException?) {
    }

    override fun onTLSHandshakeFailed(chain: Array<X509Certificate>) {
    }

    override fun onChannelAdded(channel: IChannel) {
    }

    override fun onChannelStateUpdated(channel: IChannel) {
    }

    override fun onChannelRemoved(channel: IChannel) {
    }

    override fun onChannelPermissionsUpdated(channel: IChannel) {
    }

    override fun onUserConnected(user: IUser) {
    }

    override fun onUserStateUpdated(user: IUser) {
    }

    override fun onUserTalkStateUpdated(user: IUser) {
    }

    override fun onUserJoinedChannel(user: IUser, newChannel: IChannel, oldChannel: IChannel) {
    }

    override fun onUserRemoved(user: IUser, reason: String?) {
    }

    override fun onPermissionDenied(reason: String?) {
    }

    override fun onMessageLogged(message: IMessage?) {
    }

    override fun onVoiceTargetChanged(mode: VoiceTargetMode?) {
    }

    override fun onLogInfo(message: String) {
    }

    override fun onLogWarning(message: String) {
    }

    override fun onLogError(message: String) {
    }
}