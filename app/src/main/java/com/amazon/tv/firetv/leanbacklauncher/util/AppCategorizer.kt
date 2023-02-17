package com.amazon.tv.firetv.leanbacklauncher.util

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import com.amazon.tv.firetv.leanbacklauncher.apps.AppCategory

object AppCategorizer {

    private val VIDEO_FILTER = arrayOf(
        "abema",
        "air.com.vudu",
        "android.rbtv",
        "anilabx3",
        "anixartd",
        "appletv",
        "bbc.iplayer",
        "beeline.ott",
        "bloomberg",
        "brouken.player",
        "catchplay",
        "cbs",
        "cn.tv",
        "com.canal.android",
        "com.daserste.daserste",
        "com.google.android.tv",
        "com.lgi.orionandroid.tv",
        "com.nhl",
        "com.plexapp",
        "com.prosto.tv",
        "com.tvp.vodtv",
        "com.vgtrk",
        "com.viaplay.android",
        "com.zatoo.player",
        "courville.nova",
        "ctcmediagroup.ctc",
        "dailymotion",
        "de.swr.ard",
        "com.disney.disneyplus",
        "divantv",
        "domatv.app",
        "dramafever",
        "dtv.tvx",
        "dutafilm",
        "elevenpoland",
        "exoplayer",
        "facebook",
        "filimons.soap4all",
        "filmbluray",
        "filmix",
        "filmsclub",
        "firebat",
        "foxnews",
        "foxnow",
        "fujitv",
        "fxnow",
        "gyao",
        "haystack",
        "hbo",
        "hedwig",
        "hktv.reborn",
        "hopwatch",
        "hotstar",
        "hulu",
        "iflix",
        "imdb.tv",
        "iptvclient.android.idmnc",
        "iptvextremepro",
        "iptvremote",
        "iptvsmarterstvbox",
        "iqiyi",
        "khd.app",
        "kinopub",
        "kinotrend",
        "kodi",
        "lampa",
        "lazycatsoft",
        "lionsgateplay",
        "loklok",
        "maxstream",
        "megafon.tv",
        "megogo",
        "molatvbox",
        "movies",
        "mrmc",
        "mts.mtstv",
        "mtv",
        "mycujooplayer",
        "mubi",
        "nathnetwork",
        "netversetv",
        "netflix",
        "niklabs.pp",
        "ontv",
        "ott_play",
        "ottclub",
        "ottnavigator",
        "ottplay",
        "pl.cda.tv",
        "pl.polsatgo",
        "pl.redefine.ipla",
        "pl.tvn.player.tv",
        "pl.tvp.tvp_sport",
        "planeta.tv",
        "player.clouddy.online",
        "player.online",
        "raintv",
        "rctiplus.atv",
        "redbull.rbtv",
        "ru.beeline.tv",
        "ru.cn.tv",
        "ru.ctv",
        "ru.ivi",
        "ru.kinopoisk",
        "ru.more.play",
        "ru.ntv",
        "ru.rutube",
        "ru.start",
        "ru.tv1",
        "ru.vokino",
        "ru.yourok",
        "schabi",
        "serials",
        "showtime",
        "simon.kaelae",
        "skyking",
        "smarttubetv",
        "smarters",
        "smartersplayer",
        "smartup.tv",
        "smotreshka",
        "spb.tv",
        "spbtv",
        "starz",
        "tencent.qqlivei18n",
        "tiktok",
        "tnt_premier",
        "toggo",
        "tricoloronline",
        "tubitv",
        "tv.a24h",
        "tv.kartina",
        "tv.kidoodle",
        "tv.mola",
        "tv.mts",
        "tv.ntvplus",
        "tv.okko",
        "tv.pluto",
        "tv.vintera",
        "tv2x2",
        "tvbanywhereusa",
        "tvbusa.encore.tv",
        "tvb.mytvsuper",
        "tver",
        "tviztv.tviz",
        "tvplayer",
        "twitch",
        "twittertv",
        "txcom.vplayer",
        "ua.youtv",
        "ufc.android",
        "unext",
        "useetv",
        "uz.allplay.apptv",
        "uz.i_tv.player",
        "videomanager.kids",
        "vidio.android",
        "viewbox",
        "viki",
        "visionplus",
        "vimuhd",
        "viu.tv",
        "viutv",
        "vplay",
        "wifire",
        "wink",
        "youtube",
        "yuriev.ndr",
        "zona",
        "com.peacock.peacockfiretv"
    )
    private val TV_FILTER = arrayOf(
        "audials",
        "audioplayer",
        "deezer",
        "fmplay",
        "ituner",
        "jooxtv",
        "music",
        "pandora",
        "radio",
        "somafm",
        "spotify",
        "tidal",
        "tunein",
        "zaycev",
        "com.att.tv",
        "com.cw.tv.android",
        "com.fox.now",
        "com.disney.datg.videoplatforms.android.amazon.kindle.abc"
    )
    private val SPORTS_FILTER = arrayOf(
        "android.play.games",
        "signaltalk",
        "tetris",
        "com.google.stadia",
        "com.flipps.fitetv",
        "com.njpwworld.NJPWWORLD",
        "com.espn.gtv",
        "com.sports.bleacherreport"
    )

    @Suppress("DEPRECATION")
    fun getAppCategory(pkgName: String?, actInfo: ActivityInfo?): AppCategory {
        pkgName?.let { pn ->
            for (s in VIDEO_FILTER) {
                if (pn.contains(s)) {
                    return AppCategory.VIDEO
                }
            }
            for (s in TV_FILTER) {
                if (pn.contains(s)) {
                    return AppCategory.TV
                }
            }
            for (s in SPORTS_FILTER) {
                if (pn.contains(s)) {
                    return AppCategory.SPORTS
                }
            }
        }
        actInfo?.let { ai ->
            if (ai.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0 ||
                ai.applicationInfo.metaData != null && ai.applicationInfo.metaData.getBoolean(
                    "isGame",
                    false
                )
            ) {
                return AppCategory.SPORTS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (ai.applicationInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> {
                        return AppCategory.SPORTS
                    }
                    ApplicationInfo.CATEGORY_VIDEO -> {
                        return AppCategory.VIDEO
                    }
                    ApplicationInfo.CATEGORY_AUDIO -> {
                        return AppCategory.TV
                    }
                    ApplicationInfo.CATEGORY_ACCESSIBILITY,
                    ApplicationInfo.CATEGORY_IMAGE,
                    ApplicationInfo.CATEGORY_MAPS,
                    ApplicationInfo.CATEGORY_NEWS,
                    ApplicationInfo.CATEGORY_PRODUCTIVITY,
                    ApplicationInfo.CATEGORY_SOCIAL,
                    ApplicationInfo.CATEGORY_UNDEFINED -> {
                        return AppCategory.OTHER
                    }
                }
            }
        }
        return AppCategory.OTHER
    }
}
