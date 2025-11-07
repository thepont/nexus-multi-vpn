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
     * 
     * Note: Package names may vary by region/build.
     * Apps with server-side checks (SIM, GPS) may still detect location.
     */
    val KNOWN_APPS = listOf(
        // ========================================================================
        // UK - United Kingdom
        // ========================================================================
        
        // UK Streaming (Free services)
        AppInfo("com.bbc.iplayer", "BBC iPlayer", "UK", "Streaming"),
        AppInfo("bbc.iplayer.android", "BBC iPlayer", "UK", "Streaming"),
        AppInfo("com.itv.itvplayer", "ITVX", "UK", "Streaming"),
        AppInfo("uk.co.itv.player", "ITV Hub", "UK", "Streaming"),
        AppInfo("com.channel4.ondemand", "All 4", "UK", "Streaming"),
        AppInfo("com.channel4.od", "Channel 4", "UK", "Streaming"),
        AppInfo("com.channel5.my5", "My5", "UK", "Streaming"),
        AppInfo("air.tv.my5.five", "Channel 5", "UK", "Streaming"),
        
        // UK Streaming (Subscription)
        AppInfo("com.bskyb.skygo", "Sky Go", "UK", "Streaming"),
        AppInfo("com.sky.sports.skylive", "Sky Go", "UK", "Streaming"),
        AppInfo("uk.co.nowtv.nowtv", "NOW", "UK", "Streaming"),
        
        // UK Retail / Loyalty
        AppInfo("com.tesco.clubcard", "Tesco Clubcard", "UK", "Retail"),
        AppInfo("com.j_sainsbury.nectar", "Sainsbury's Nectar", "UK", "Retail"),
        AppInfo("com.boots.bootsapp", "Boots", "UK", "Retail"),
        AppInfo("co.uk.greggs.app", "Greggs", "UK", "Retail"),
        
        // UK Banking
        AppInfo("co.uk.getmondo", "Monzo Bank", "UK", "Banking"),
        
        // ========================================================================
        // US - United States
        // ========================================================================
        
        // US Streaming
        AppInfo("com.hulu.plus", "Hulu", "US", "Streaming"),
        AppInfo("com.hbo.hbonow", "HBO Max", "US", "Streaming"),
        AppInfo("com.showtime.standalone", "Showtime", "US", "Streaming"),
        AppInfo("com.peacocktv.peacockandroid", "Peacock", "US", "Streaming"),
        AppInfo("com.cbs.app", "Paramount+", "US", "Streaming"),
        AppInfo("com.espn.score_center", "ESPN", "US", "Streaming"),
        AppInfo("com.google.android.apps.youtube.tv", "YouTube TV", "US", "Streaming"),
        AppInfo("com.sling", "Sling TV", "US", "Streaming"),
        
        // US Music
        AppInfo("com.pandora.android", "Pandora", "US", "Music"),
        
        // US Finance / Payments
        AppInfo("com.venmo", "Venmo", "US", "Finance"),
        AppInfo("com.squareup.cash", "Cash App", "US", "Finance"),
        AppInfo("com.robinhood.android", "Robinhood", "US", "Finance"),
        
        // US Retail
        AppInfo("com.target.ui", "Target", "US", "Retail"),
        
        // ========================================================================
        // FR - France
        // ========================================================================
        
        // France Streaming
        AppInfo("fr.francetv.pluzz", "France.tv", "FR", "Streaming"),
        AppInfo("com.canalplus.mycanal", "myCANAL", "FR", "Streaming"),
        AppInfo("com.tf1.fr", "TF1", "FR", "Streaming"),
        AppInfo("fr.m6.m6replay", "6play", "FR", "Streaming"),
        
        // France News
        AppInfo("com.france24.androidapp", "France 24", "FR", "News"),
        
        // ========================================================================
        // AU - Australia
        // ========================================================================
        
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
        
        // ========================================================================
        // DE - Germany
        // ========================================================================
        
        // Germany Streaming
        AppInfo("de.zdf.android.mediathek", "ZDF Mediathek", "DE", "Streaming"),
        AppInfo("de.ard.audiothek", "ARD Mediathek", "DE", "Streaming"),
        
        // ========================================================================
        // JP - Japan
        // ========================================================================
        
        // Japan Streaming
        AppInfo("tv.abema", "AbemaTV", "JP", "Streaming"),
        AppInfo("com.nttdocomo.android.danimestore", "dAnime Store", "JP", "Streaming"),
        AppInfo("jp.unext.mediaplayer", "U-NEXT", "JP", "Streaming"),
        AppInfo("jp.co.ntv.tverapp", "TVer", "JP", "Streaming"),
        AppInfo("jp.nhk.netradio", "NHK", "JP", "Streaming"),
        
        // Japan Gaming
        AppInfo("com.aniplex.fategrandorder", "Fate/Grand Order", "JP", "Gaming"),
        
        // Japan Finance / Payments
        AppInfo("jp.ne.paypay.android.app", "PayPay", "JP", "Finance"),
        
        // Japan Retail
        AppInfo("jp.co.lawson.android.app", "Lawson", "JP", "Retail"),
        
        // ========================================================================
        // KR - South Korea
        // ========================================================================
        
        // South Korea Maps / Navigation
        AppInfo("com.nhn.android.nmap", "Naver Maps", "KR", "Maps"),
        AppInfo("net.daum.android.map", "KakaoMap", "KR", "Maps"),
        
        // South Korea Transport
        AppInfo("com.kakao.taxi", "Kakao T", "KR", "Transport"),
        
        // South Korea Marketplace
        AppInfo("com.towneers.dn.karrot", "Karrot", "KR", "Marketplace"),
        
        // South Korea Streaming
        AppInfo("net.cjenm.tving", "TVING", "KR", "Streaming"),
        AppInfo("kr.co.captv.pooq", "Wavve", "KR", "Streaming"),
        
        // ========================================================================
        // IN - India
        // ========================================================================
        
        // India Streaming (Cricket/Sports)
        AppInfo("in.startv.hotstar", "Disney+ Hotstar", "IN", "Streaming"),
        AppInfo("com.jio.media.ondemand", "JioCinema", "IN", "Streaming"),
        
        // India Finance / Payments
        AppInfo("com.phonepe.app", "PhonePe", "IN", "Finance"),
        
        // India Retail
        AppInfo("com.flipkart.android", "Flipkart", "IN", "Retail"),
        
        // ========================================================================
        // MULTIPLE - Library-Locked Apps (Content varies by region)
        // ========================================================================
        // These apps work everywhere but show different content based on location
        
        // Streaming (Library varies)
        AppInfo("com.netflix.mediaclient", "Netflix", "Multiple", "Streaming"),
        AppInfo("com.amazon.avod.thirdpartyclient", "Prime Video", "Multiple", "Streaming"),
        AppInfo("com.disney.disneyplus", "Disney+", "Multiple", "Streaming"),
        AppInfo("com.google.android.youtube", "YouTube", "Multiple", "Streaming"),
        AppInfo("com.britbox.us", "BritBox", "Multiple", "Streaming"),
        
        // Music (Library varies)
        AppInfo("com.spotify.music", "Spotify", "Multiple", "Music"),
        
        // Sports (Multi-region)
        AppInfo("com.dazn", "DAZN", "Multiple", "Sports"),
        AppInfo("com.nba.gametime", "NBA", "US", "Sports"),
        AppInfo("com.nfl.mobile", "NFL", "US", "Sports"),
        
        // News (Multiple editions)
        AppInfo("com.guardian", "The Guardian", "Multiple", "News"),
        AppInfo("com.ft.news", "Financial Times", "Multiple", "News"),
        AppInfo("com.nytimes.android", "NY Times", "Multiple", "News"),
        
        // UK Services (additional)
        AppInfo("air.ITVMediaPlayer", "ITV Player", "UK", "Streaming")
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

