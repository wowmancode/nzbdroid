package com.owan.nzbdroid

import android.content.Context
import com.owan.nzbdroid.data.indexer.IndexerSearchRepository
import com.owan.nzbdroid.data.settings.SettingsRepository

/**
 * Tiny hand-rolled dependency container (no Hilt/Dagger needed for an app this size).
 * Holds the one long-lived repository (settings) plus stateless helpers; NZBGet/SMB
 * clients are cheap and built fresh from current settings wherever they're used, since
 * their target server can change at any time from the Settings screen.
 */
class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = SettingsRepository.getInstance(context)
    val indexerSearchRepository: IndexerSearchRepository = IndexerSearchRepository()
}
