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

import android.Manifest
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.Socket
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.spongycastle.util.encoders.Hex
import se.lublin.humla.HumlaService
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.Server
import se.lublin.humla.protobuf.Mumble
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import se.lublin.humla.util.MumbleURLParser
import se.lublin.mumla.BuildConfig
import se.lublin.mumla.R
import se.lublin.mumla.Settings
import se.lublin.mumla.app.DrawerAdapter.DrawerDataProvider
import se.lublin.mumla.channel.AccessTokenFragment
import se.lublin.mumla.channel.ChannelFragment
import se.lublin.mumla.channel.ServerInfoFragment
import se.lublin.mumla.db.DatabaseCertificate
import se.lublin.mumla.db.DatabaseProvider
import se.lublin.mumla.db.MumlaDatabase
import se.lublin.mumla.db.MumlaSQLiteDatabase
import se.lublin.mumla.db.PublicServer
import se.lublin.mumla.preference.MumlaCertificateGenerateTask
import se.lublin.mumla.preference.Preferences
import se.lublin.mumla.servers.FavouriteServerListFragment
import se.lublin.mumla.servers.FavouriteServerListFragment.ServerConnectHandler
import se.lublin.mumla.servers.PublicServerListFragment
import se.lublin.mumla.servers.ServerEditFragment
import se.lublin.mumla.servers.ServerEditFragment.ServerEditListener
import se.lublin.mumla.service.IMumlaService
import se.lublin.mumla.service.MumlaService
import se.lublin.mumla.service.MumlaService.MumlaBinder
import se.lublin.mumla.util.HumlaServiceFragment
import se.lublin.mumla.util.HumlaServiceProvider
import se.lublin.mumla.util.MumlaTrustStore

class MumlaCallActivity : AppCompatActivity(), AdapterView.OnItemClickListener,
    ServerConnectHandler, HumlaServiceProvider, DatabaseProvider, OnSharedPreferenceChangeListener,
    DrawerDataProvider, ServerEditListener {
    private var mService: IMumlaService? = null
    private val mDatabase: MumlaDatabase by lazy {
        MumlaSQLiteDatabase(this).also {
            it.open()
        }
    }
    private val mSettings: Settings by lazy {
        Settings.getInstance(this)
    }
    private val mViewModel: MumlaCallViewModel by viewModel()

    private var mDrawerToggle: ActionBarDrawerToggle? = null
    private var mDrawerLayout: DrawerLayout? = null
    private var mDrawerList: ListView? = null
    private var mDrawerAdapter: DrawerAdapter? = null

    private var mPermPostNotificationsAsked = false

    private var mConnectingDialog: ProgressDialog? = null
    private var mErrorDialog: AlertDialog? = null
    private var mDisconnectPromptBuilder: AlertDialog.Builder? = null

    /** List of fragments to be notified about service state changes.  */
    private val mServiceFragments: MutableList<HumlaServiceFragment> =
        ArrayList<HumlaServiceFragment>()

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            mService = (service as MumlaBinder).service
            mService!!.setSuppressNotifications(true)
            mService!!.registerObserver(mObserver)
            mService!!.registerObserver(mViewModel)
            mService!!.clearChatNotifications() // Clear chat notifications on resume.
            mDrawerAdapter!!.notifyDataSetChanged()

            for (fragment in mServiceFragments) fragment.setServiceBound(true)

            // Re-show server list if we're showing a fragment that depends on the service.
            if (supportFragmentManager.findFragmentById(R.id.content_frame) is HumlaServiceFragment && !mService!!.isConnected()) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
            updateConnectionState(getService()!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mService = null
        }
    }

    private val mObserver: HumlaObserver = object : HumlaObserver() {
        override fun onConnected(msg: Mumble.ServerSync) {
            if (mSettings.shouldStartUpInPinnedMode()) {
                loadDrawerFragment(DrawerAdapter.ITEM_PINNED_CHANNELS)
            } else {
                loadDrawerFragment(DrawerAdapter.ITEM_SERVER)
            }

            mDrawerAdapter!!.notifyDataSetChanged()
            supportInvalidateOptionsMenu()

            updateConnectionState(service!!)
        }

        override fun onConnecting() {
            updateConnectionState(service!!)
        }

        override fun onDisconnected(e: HumlaException?) {
            // Re-show server list if we're showing a fragment that depends on the service.
            if (supportFragmentManager.findFragmentById(R.id.content_frame) is HumlaServiceFragment) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
            mDrawerAdapter!!.notifyDataSetChanged()
            supportInvalidateOptionsMenu()

            updateConnectionState(service!!)
        }

        override fun onTLSHandshakeFailed(chain: Array<X509Certificate>) {
            val lastServer = service!!.getTargetServer()

            if (chain.size == 0) return

            try {
                val x509 = chain[0]

                val adb = AlertDialog.Builder(this@MumlaCallActivity)
                adb.setTitle(R.string.untrusted_certificate)
                val layout = layoutInflater.inflate(R.layout.certificate_info, null)
                val text = layout.findViewById<TextView>(R.id.certificate_info_text)
                try {
                    val digest1 = MessageDigest.getInstance("SHA-1")
                    val digest2 = MessageDigest.getInstance("SHA-256")
                    val hexDigest1 = String(Hex.encode(digest1.digest(x509.encoded))).replace(
                        "(..)".toRegex(), "$1:"
                    )
                    val hexDigest2 = String(Hex.encode(digest2.digest(x509.encoded))).replace(
                        "(..)".toRegex(), "$1:"
                    )

                    text.text = getString(
                        R.string.certificate_info,
                        x509.subjectDN.name,
                        x509.notBefore.toString(),
                        x509.notAfter.toString(),
                        hexDigest1.substring(0, hexDigest1.length - 1),
                        hexDigest2.substring(0, hexDigest2.length - 1)
                    )
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                    adb.setMessage(x509.toString())
                }
                adb.setView(layout)
                adb.setPositiveButton(R.string.allow, object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        // Try to add to trust store
                        try {
                            val alias = lastServer.host
                            val trustStore = MumlaTrustStore.getTrustStore(this@MumlaCallActivity)
                            trustStore.setCertificateEntry(alias, x509)
                            MumlaTrustStore.saveTrustStore(this@MumlaCallActivity, trustStore)
                            Toast.makeText(
                                this@MumlaCallActivity, R.string.trust_added, Toast.LENGTH_LONG
                            ).show()
                            connectToServer(lastServer)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(
                                this@MumlaCallActivity, R.string.trust_add_failed, Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                })
                adb.setNegativeButton(android.R.string.cancel, null)
                adb.show()
            } catch (e: CertificateException) {
                e.printStackTrace()
            }
        }

        override fun onPermissionDenied(reason: String?) {
            val adb = AlertDialog.Builder(this@MumlaCallActivity)
            adb.setTitle(R.string.perm_denied)
            adb.setMessage(reason)
            adb.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(mSettings.getTheme())



        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_mumble)

        setStayAwake(mSettings.shouldStayAwake())

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)

        mDrawerLayout = findViewById<View?>(R.id.drawer_layout) as DrawerLayout
        mDrawerList = findViewById<View?>(R.id.left_drawer) as ListView
        mDrawerList!!.onItemClickListener = this
        mDrawerAdapter = DrawerAdapter(this, this)
        mDrawerList!!.setAdapter(mDrawerAdapter)
        mDrawerToggle = object : ActionBarDrawerToggle(
            this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close
        ) {
            override fun onDrawerClosed(drawerView: View) {
                supportInvalidateOptionsMenu()
            }

            override fun onDrawerStateChanged(newState: Int) {
                super.onDrawerStateChanged(newState)
                // Prevent push to talk from getting stuck on when the drawer is opened.
                if (service != null && service!!.isConnected()) {
                    val session = service!!.HumlaSession()
                    if (session.isTalking() && !mSettings.isPushToTalkToggle) {
                        session.setTalkingState(false)
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                supportInvalidateOptionsMenu()
            }
        }

        mDrawerLayout!!.setDrawerListener(mDrawerToggle)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        val dadb = AlertDialog.Builder(this)
        dadb.setPositiveButton(R.string.confirm, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                if (mService != null) mService!!.disconnect()
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
        })
        dadb.setNegativeButton(android.R.string.cancel, null)
        mDisconnectPromptBuilder = dadb

        if (savedInstanceState == null) {
            if (intent != null && intent.hasExtra(EXTRA_DRAWER_FRAGMENT)) {
                loadDrawerFragment(
                    intent.getIntExtra(
                        EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_FAVOURITES
                    )
                )
            } else {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }
        }

        // If we're given a Mumble URL to show, open up a server edit fragment.
        if (intent != null && Intent.ACTION_VIEW == intent.action) {
            val url = intent.dataString
            try {
                val server = MumbleURLParser.parseURL(url)

                // Open a dialog prompting the user to connect to the Mumble server.
                val fragment = ServerEditFragment.createServerEditDialog(
                    this@MumlaCallActivity, server, ServerEditFragment.Action.CONNECT_ACTION, true
                )
                fragment.show(supportFragmentManager, "url_edit")
            } catch (e: MalformedURLException) {
                Toast.makeText(this, getString(R.string.mumble_url_parse_failed), Toast.LENGTH_LONG)
                    .show()
                e.printStackTrace()
            }
        }

        volumeControlStream =
            if (mSettings.isHandsetMode) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC

//        if (mSettings!!.isFirstRun()) {
//            showFirstRunGuide()
//        }

        requestPermission()

        // Get parameters from intent
        val fromUser = intent.getStringExtra(USER) ?: ""
        val toCorp = intent.getStringExtra(CORP) ?: ""
        val aboutJob = intent.getStringExtra(JOB_TITLE) ?: ""


        lifecycleScope.launch {
            generateCert()
            delay(5000)
            //Channel Name 不能有特殊字元【資訊科技】
            mViewModel.connectServer("${toCorp}${Random.nextInt() % 10}".trim())
            if (fromUser.isEmpty()) return@launch
        }

    }

    private fun observeFlows() {
        lifecycleScope.launch {
            mViewModel.serverStateFlow.collect {
                when (it) {
                    is ServiceState.CONNECTED -> {
                        mViewModel.createChannel(it.channelName)
                    }

                    is ServiceState.CONNECTING -> {}
                    is ServiceState.DISCONNECTED -> {
                        if (it.reconnect) {
                            delay(it.retryBackOff.seconds)
                            mViewModel.connectServer(it.channelName)
                        }
                    }

                    is ServiceState.DISCONNECTING -> {}
                    is ServiceState.JOINED -> {}
                }
                println(">>> State $it")
            }
        }
        lifecycleScope.launch {
            mViewModel.actionSharedFlow.collect {
                when (it) {
                    is ServiceAction.CONNECT -> {
                        val connectTask = ServerConnectTask(this@MumlaCallActivity, mDatabase)
                        connectTask.execute(it.server)
                    }

                    ServiceAction.DISCONNECT -> {
                        mService?.let { service ->
                            if (service.isConnected) {
                                service.disconnect()
                            }
                        }
                    }

                    is ServiceAction.CREATE_CHANNEL -> {
                        // 這邊要找 "openchannel, 但是 這時候 session channel 是？"
                        val sessionChannel = mService?.HumlaSession()?.sessionChannel
                        val openChannelId = ((sessionChannel?.subchannels ?: emptyList()) + sessionChannel)
                            .firstOrNull { sub ->
                                sub?.name == "openchannel"
                            }?.id ?: 0
                        createNewChannel(it.name, openChannelId, it.name)
                    }

                    is ServiceAction.JOIN_CHANNEL -> {
                        mService?.HumlaSession()?.joinChannel(it.channel.id)
                    }

                    ServiceAction.REGISTER -> {
                        val sessionId = mService?.HumlaSession()?.sessionId ?: return@collect
                        mService?.HumlaSession()?.registerUser(sessionId)
                    }
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle!!.syncState()
    }

    override fun onResume() {
        super.onResume()
        val connectIntent = Intent(this, MumlaService::class.java)
        bindService(connectIntent, mConnection, 0)
    }

    override fun onPause() {
        super.onPause()
        if (mErrorDialog != null) mErrorDialog!!.dismiss()
        if (mConnectingDialog != null) mConnectingDialog!!.dismiss()

        if (mService != null) {
            for (fragment in mServiceFragments) {
                fragment.setServiceBound(false)
            }
            mService!!.unregisterObserver(mObserver)
            mService!!.setSuppressNotifications(false)
        }
        unbindService(mConnection)
    }

    override fun onDestroy() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        mDatabase.close()
        super.onDestroy()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val disconnectButton = menu.findItem(R.id.action_disconnect)
        disconnectButton.isVisible = mService != null && mService!!.isConnected()

        // Color the action bar icons to the primary text color of the theme.
        val foregroundColor = supportActionBar!!.themedContext
            .obtainStyledAttributes(intArrayOf(android.R.attr.textColor)).getColor(0, -1)
        for (x in 0..<menu.size()) {
            val item = menu.getItem(x)
            if (item.icon != null) {
                val icon = item.icon!!
                    .mutate() // Mutate the icon so that the color filter is exclusive to the action bar
                icon.setColorFilter(foregroundColor, PorterDuff.Mode.MULTIPLY)
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.mumla, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mDrawerToggle!!.onOptionsItemSelected(item)) return true
        if (item.itemId == R.id.action_disconnect) {
            service!!.disconnect()
            return true
        }
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle!!.onConfigurationChanged(newConfig)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (mService != null && keyCode == mSettings.pushToTalkKey) {
            mService!!.onTalkKeyDown()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (mService != null && keyCode == mSettings.pushToTalkKey) {
            mService!!.onTalkKeyUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        if (mService != null && mService!!.isConnected()) {
            mDisconnectPromptBuilder!!.setMessage(
                getString(
                    R.string.disconnectSure, mService!!.getTargetServer().name
                )
            )
            mDisconnectPromptBuilder!!.show()
            return
        }
        super.onBackPressed()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        mDrawerLayout!!.closeDrawers()
        loadDrawerFragment(id.toInt())
    }

    private fun generateCert() {
        val generateTask: MumlaCertificateGenerateTask =
            object : MumlaCertificateGenerateTask(this@MumlaCallActivity) {
                override fun onPostExecute(result: DatabaseCertificate?) {
                    super.onPostExecute(result)
                    if (result != null) {
                        Log.d(TAG, "Set default cert id ${result.id}")
                        mSettings.setDefaultCertificateId(result.id)
                    }
                }
            }
        generateTask.execute()
    }

//    private fun showFirstRunGuide() {
//        // Prompt the user to generate a certificate.
//        if (mSettings!!.isUsingCertificate()) {
//            return
//        }
//        val adb = AlertDialog.Builder(this)
//        adb.setTitle(R.string.first_run_generate_certificate_title)
//        var msg = getString(R.string.first_run_generate_certificate)
//        if (BuildConfig.FLAVOR == "donation") {
//            msg = getString(R.string.donation_thanks) + "\n\n" + msg
//        }
//        adb.setMessage(msg)
//        adb.setPositiveButton(R.string.generate, object : DialogInterface.OnClickListener {
//            override fun onClick(dialog: DialogInterface?, which: Int) {
//                val generateTask: MumlaCertificateGenerateTask =
//                    object : MumlaCertificateGenerateTask(this@MumlaCallActivity) {
//                        override fun onPostExecute(result: DatabaseCertificate?) {
//                            super.onPostExecute(result)
//                            if (result != null) mSettings!!.setDefaultCertificateId(result.getId())
//                        }
//                    }
//                generateTask.execute()
//            }
//        })
//        adb.show()
//        mSettings!!.setFirstRun(false)
//    }

    /**
     * Loads a fragment from the drawer.
     */
    private fun loadDrawerFragment(fragmentId: Int) {
        var fragmentClass: Class<out Fragment?>? = null
        val args = Bundle()
        when (fragmentId) {
            DrawerAdapter.ITEM_SERVER -> fragmentClass = ChannelFragment::class.java
            DrawerAdapter.ITEM_INFO -> fragmentClass = ServerInfoFragment::class.java
            DrawerAdapter.ITEM_ACCESS_TOKENS -> {
                fragmentClass = AccessTokenFragment::class.java
                val connectedServer = service!!.getTargetServer()
                args.putLong("server", connectedServer.id)
                args.putStringArrayList(
                    "access_tokens",
                    mDatabase.getAccessTokens(connectedServer.id) as ArrayList<String?>?
                )
            }

            DrawerAdapter.ITEM_PINNED_CHANNELS -> {
                fragmentClass = ChannelFragment::class.java
                args.putBoolean("pinned", true)
            }

            DrawerAdapter.ITEM_FAVOURITES -> fragmentClass = FavouriteServerListFragment::class.java
            DrawerAdapter.ITEM_PUBLIC -> fragmentClass = PublicServerListFragment::class.java
            DrawerAdapter.ITEM_SETTINGS -> {
                val prefIntent = Intent(this, Preferences::class.java)
                startActivity(prefIntent)
                return
            }

            else -> return
        }
        val fragment = Fragment.instantiate(this, fragmentClass.getName(), args)
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment, fragmentClass.getName())
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE).commit()
        setTitle(mDrawerAdapter!!.getItemWithId(fragmentId).title)
    }

    override fun connectToServer(server: Server?) {
//        connectToServerWithPerm()
    }

    fun createNewChannel(channelName: String, parentChannelId: Int, description: String? = null) {
        mService?.HumlaSession()?.createChannel(parentChannelId, channelName, description, 0, true, 2)
    }

    fun requestPermission() {
        if (ContextCompat.checkSelfPermission(
                this@MumlaCallActivity, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@MumlaCallActivity,
                arrayOf<String>(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
            return
        } else {
            observeFlows()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !mPermPostNotificationsAsked) {
            if (ContextCompat.checkSelfPermission(
                    this@MumlaCallActivity, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MumlaCallActivity,
                    arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSIONS_REQUEST_POST_NOTIFICATIONS
                )
                return
            }
        } else {
            observeFlows()
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        if (grantResults.size == 0) {
            return
        }

        when (requestCode) {
            PERMISSIONS_REQUEST_RECORD_AUDIO -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                observeFlows()
            } else {
                Toast.makeText(
                    this@MumlaCallActivity,
                    getString(R.string.grant_perm_microphone),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }

            PERMISSIONS_REQUEST_POST_NOTIFICATIONS -> {
                mPermPostNotificationsAsked = true
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    // This is inspired by https://stackoverflow.com/a/34612503
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MumlaCallActivity, Manifest.permission.POST_NOTIFICATIONS
                        )
                    ) {
                        Toast.makeText(
                            this@MumlaCallActivity,
                            getString(R.string.grant_perm_notifications),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun isPortOpen(host: String?, port: Int, timeout: Int): Boolean {
        val open = AtomicBoolean(false)
        try {
            val thread = Thread(object : Runnable {
                override fun run() {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(host, port), timeout)
                        socket.close()
                        open.set(true)
                    } catch (e: Exception) {
                        Log.d(TAG, "isPortOpen() run()" + e)
                    }
                }
            })
            thread.start()
            thread.join()
            return open.get()
        } catch (e: Exception) {
            Log.d(TAG, "isPortOpen() " + e)
        }
        return false
    }

    override fun connectToPublicServer(server: PublicServer) {
        val alertBuilder = AlertDialog.Builder(this)

        val settings = Settings.getInstance(this)

        // Allow username entry
        val usernameField = EditText(this)
        usernameField.setHint(settings.defaultUsername)
        alertBuilder.setView(usernameField)

        alertBuilder.setTitle(R.string.connectToServer)

        alertBuilder.setPositiveButton(R.string.connect, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                val newServer = server
                if (usernameField.getText()
                        .toString() != ""
                ) newServer.username = usernameField.getText().toString()
                else newServer.username = settings.defaultUsername
                connectToServer(newServer)
            }
        })

        alertBuilder.show()
    }

    private fun setStayAwake(stayAwake: Boolean) {
        if (stayAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Updates the activity to represent the connection state of the given service.
     * Will show reconnecting dialog if reconnecting, dismiss otherwise, etc.
     * Basically, this service will do catch-up if the activity wasn't bound to receive
     * connection state updates.
     * @param service A bound IHumlaService.
     */
    private fun updateConnectionState(service: IHumlaService) {
        if (mConnectingDialog != null) mConnectingDialog!!.dismiss()
        if (mErrorDialog != null) mErrorDialog!!.dismiss()

        when (mService!!.getConnectionState()) {
            HumlaService.ConnectionState.CONNECTING -> {
                val server = service.getTargetServer()
                mConnectingDialog = ProgressDialog(this)
                mConnectingDialog!!.isIndeterminate = true
                mConnectingDialog!!.setCancelable(true)
                mConnectingDialog!!.setOnCancelListener(object : DialogInterface.OnCancelListener {
                    override fun onCancel(dialog: DialogInterface?) {
                        mService!!.disconnect()
                        Toast.makeText(
                            this@MumlaCallActivity, R.string.cancelled, Toast.LENGTH_SHORT
                        ).show()
                    }
                })
                // SRV lookup is done later, so we no longer show the port (and
                // only the configured hostname)
                mConnectingDialog!!.setMessage(
                    getString(
                        R.string.connecting_to_server, server.host
                    ) + (if (mSettings.isTorEnabled) " (Tor)" else "")
                )
                mConnectingDialog!!.show()
            }

            HumlaService.ConnectionState.CONNECTION_LOST ->                 // Only bother the user if the error hasn't already been shown.
                if (getService() != null && !getService()!!.isErrorShown()) {
                    // TODO? bail out if service gone -- it is happening!
                    if (getService() == null) {
                        return
                    }
                    val ab = Builder(this@MumlaCallActivity)
                    ab.setTitle(getString(R.string.connectionRefused) + (if (mSettings.isTorEnabled) " (Tor)" else ""))
                    val error = getService()!!.getConnectionError()
                    if (error != null && mService!!.isReconnecting()) {
                        ab.setMessage(
                            (error.message + "\n\n" + getString(
                                R.string.attempting_reconnect,
                                if (error.cause != null) error.cause!!.message else "unknown"
                            ))
                        )
                        ab.setPositiveButton(
                            R.string.cancel_reconnect, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    if (getService() != null) {
                                        getService()!!.cancelReconnect()
                                        getService()!!.markErrorShown()
                                    }
                                }
                            })
                    } else if (error != null && error.reason == HumlaException.HumlaDisconnectReason.REJECT && (error.reject
                            .getType() == Mumble.Reject.RejectType.WrongUserPW || error.reject
                            .getType() == Mumble.Reject.RejectType.WrongServerPW)
                    ) {
                        val passwordField = EditText(this)
                        passwordField.setInputType(
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        )
                        passwordField.setHint(R.string.password)
                        ab.setTitle(R.string.invalid_password)
                        ab.setMessage(error.message)
                        ab.setView(passwordField)
                        ab.setPositiveButton(
                            R.string.reconnect, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    val server = getService()!!.getTargetServer()
                                    if (server == null) return
                                    val password = passwordField.getText().toString()
                                    server.password = password
                                    if (server.isSaved) mDatabase.updateServer(server)
                                    connectToServer(server)
                                }
                            })
                        ab.setNegativeButton(
                            android.R.string.cancel, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    if (getService() != null) getService()!!.markErrorShown()
                                }
                            })
                    } else {
                        val msg = if (error != null) error.message else getString(R.string.unknown)
                        ab.setMessage(msg)
                        ab.setPositiveButton(
                            android.R.string.ok, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    if (getService() != null) getService()!!.markErrorShown()
                                }
                            })
                    }
                    ab.setCancelable(false)
                    mErrorDialog = ab.show()
                }

            HumlaService.ConnectionState.DISCONNECTED -> {}
            HumlaService.ConnectionState.CONNECTED -> {
                Log.d(TAG, "CONNECTED: ${mService?.HumlaSession()?.serverSettings?.welcomeText}")
            }
        }
    }

    /*
     * HERE BE IMPLEMENTATIONS
     */
    override fun getService(): IMumlaService? {
        return mService
    }

    override fun getDatabase(): MumlaDatabase {
        return mDatabase
    }

    override fun addServiceFragment(fragment: HumlaServiceFragment?) {
        mServiceFragments.add(fragment!!)
    }

    override fun removeServiceFragment(fragment: HumlaServiceFragment?) {
        mServiceFragments.remove(fragment)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Settings.PREF_THEME == key) {
            // Recreate activity when theme is changed
            recreate()
        } else if (Settings.PREF_STAY_AWAKE == key) {
            setStayAwake(mSettings.shouldStayAwake())
        } else if (Settings.PREF_HANDSET_MODE == key) {
            volumeControlStream =
                if (mSettings.isHandsetMode) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
        }
    }

    override fun isConnected(): Boolean {
        return mService != null && mService!!.isConnected()
    }

    override fun getConnectedServerName(): String? {
        if (mService != null && mService!!.isConnected()) {
            val server = mService!!.getTargetServer()
            return if (server.name == "") server.host else server.name
        }
        if (BuildConfig.DEBUG) throw RuntimeException("getConnectedServerName should only be called if connected!")
        return ""
    }

    override fun onServerEdited(action: ServerEditFragment.Action, server: Server?) {
        when (action) {
            ServerEditFragment.Action.ADD_ACTION -> {
                mDatabase.addServer(server)
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }

            ServerEditFragment.Action.EDIT_ACTION -> {
                mDatabase.updateServer(server)
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES)
            }

            ServerEditFragment.Action.CONNECT_ACTION -> connectToServer(server)
        }
    }

    companion object {

        val USER: String = "USER"
        val CORP: String = "CORP"
        val JOB_TITLE: String = "JOB_TITLE"

        fun launchCall(
            context: Context, fromUser: String, toCorp: CharSequence, aboutJob: CharSequence
        ) {
            val intent = Intent(context, MumlaCallActivity::class.java).apply {
                putExtra(USER, fromUser)
                putExtra(CORP, toCorp)
                putExtra(JOB_TITLE, aboutJob)
            }
            context.startActivity(intent)
        }

        fun launchAnswer(context: Context) {
            val intent = Intent(context, MumlaCallActivity::class.java)
            context.startActivity(intent)
        }

        private val TAG: String = MumlaCallActivity::class.java.getName()

        /**
         * If specified, the provided integer drawer fragment ID is shown when the activity is created.
         */
        const val EXTRA_DRAWER_FRAGMENT: String = "drawer_fragment"

        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
        private const val PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 2
    }
}
