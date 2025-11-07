package com.multiregionvpn.data

/**
 * Database of known geo-blocked apps and their recommended regions
 * 
 * Used for smart suggestions and app ordering.
 */
object GeoBlockedApps {
    
    data class AppInfo(
        val packageName: String,
        val displayName: String,
        val recommendedRegion: String,
        val category: String
    )
    
    /**
     * Known geo-blocked apps with their preferred regions
     */
    val KNOWN_APPS = listOf(
        // UK Streaming
        AppInfo("bbc.iplayer.android", "BBC iPlayer", "UK", "Streaming"),
        AppInfo("uk.co.itv.player", "ITV Hub", "UK", "Streaming"),
        AppInfo("air.tv.my5.five", "Channel 5", "UK", "Streaming"),
        AppInfo("com.channel4.ondemand", "All 4", "UK", "Streaming"),
        AppInfo("com.channel4.od", "Channel 4", "UK", "Streaming"),
        AppInfo("com.sky.sports.skylive", "Sky Go", "UK", "Streaming"),
        AppInfo("uk.co.nowtv.nowtv", "NOW", "UK", "Streaming"),
        
        // US Streaming
        AppInfo("com.hulu.plus", "Hulu", "US", "Streaming"),
        AppInfo("com.hbo.hbonow", "HBO Max", "US", "Streaming"),
        AppInfo("com.showtime.standalone", "Showtime", "US", "Streaming"),
        AppInfo("com.peacocktv.peacockandroid", "Peacock", "US", "Streaming"),
        AppInfo("com.cbs.app", "Paramount+", "US", "Streaming"),
        AppInfo("com.espn.score_center", "ESPN", "US", "Streaming"),
        
        // France Streaming
        AppInfo("fr.francetv.pluzz", "France.tv", "FR", "Streaming"),
        AppInfo("com.canalplus.mycanal", "myCANAL", "FR", "Streaming"),
        AppInfo("com.tf1.fr", "TF1", "FR", "Streaming"),
        AppInfo("fr.m6.m6replay", "6play", "FR", "Streaming"),
        
        // Australia Streaming
        AppInfo("au.net.abc.iview", "ABC iView", "AU", "Streaming"),
        AppInfo("au.net.abc.kidsiview", "ABC Kids", "AU", "Streaming"),
        AppInfo("au.net.abc.kidslisten", "ABC Kids Listen", "AU", "Streaming"),
        AppInfo("au.net.abc.news", "ABC News", "AU", "News"),
        AppInfo("com.sbs.ondemand.android", "SBS On Demand", "AU", "Streaming"),
        AppInfo("au.com.freeview.fv", "7plus", "AU", "Streaming"),
        AppInfo("au.com.tenplay", "10 Play", "AU", "Streaming"),
        AppInfo("au.com.nine.9nowandroid", "9Now", "AU", "Streaming"),
        AppInfo("au.com.optus.sport", "Optus Sport", "AU", "Streaming"),
        AppInfo("au.com.foxsports.matchcentre", "Kayo Sports", "AU", "Streaming"),
        
        // Germany Streaming
        AppInfo("de.zdf.android.mediathek", "ZDF Mediathek", "DE", "Streaming"),
        AppInfo("de.ard.audiothek", "ARD Mediathek", "DE", "Streaming"),
        
        // Japan Streaming
        AppInfo("jp.co.ntv.tverapp", "TVer", "JP", "Streaming"),
        AppInfo("jp.nhk.netradio", "NHK", "JP", "Streaming"),
        
        // Global Streaming (region-specific content)
        AppInfo("com.netflix.mediaclient", "Netflix", "Multiple", "Streaming"),
        AppInfo("com.amazon.avod.thirdpartyclient", "Prime Video", "Multiple", "Streaming"),
        AppInfo("com.disney.disneyplus", "Disney+", "Multiple", "Streaming"),
        AppInfo("com.spotify.music", "Spotify", "Multiple", "Music"),
        
        // Sports
        AppInfo("com.dazn", "DAZN", "Multiple", "Sports"),
        AppInfo("com.nba.gametime", "NBA", "US", "Sports"),
        AppInfo("com.nfl.mobile", "NFL", "US", "Sports"),
        
        // News (Multiple regions, different content)
        AppInfo("com.guardian", "The Guardian", "Multiple", "News"),
        AppInfo("com.ft.news", "Financial Times", "Multiple", "News"),
        AppInfo("com.nytimes.android", "NY Times", "Multiple", "News"),
        
        // UK/US Services (Available in multiple regions, different content)
        AppInfo("com.britbox.us", "BritBox", "Multiple", "Streaming"),
        AppInfo("air.ITVMediaPlayer", "ITV Player", "UK", "Streaming"),
        
        // France Services
        AppInfo("com.france24.androidapp", "France 24", "FR", "News")
    )
    
    /**
     * Check if an app is known to be geo-blocked
     */
    fun isGeoBlocked(packageName: String): Boolean {
        return KNOWN_APPS.any { it.packageName == packageName }
    }
    
    /**
     * Get recommended region for an app
     */
    fun getRecommendedRegion(packageName: String): String? {
        return KNOWN_APPS.find { it.packageName == packageName }?.recommendedRegion
    }
    
    /**
     * Get app info by package name
     */
    fun getAppInfo(packageName: String): AppInfo? {
        return KNOWN_APPS.find { it.packageName == packageName }
    }
    
    /**
     * Get all apps for a specific region
     */
    fun getAppsForRegion(region: String): List<AppInfo> {
        return KNOWN_APPS.filter { 
            it.recommendedRegion.equals(region, ignoreCase = true) || 
            it.recommendedRegion == "Multiple" 
        }
    }
}

