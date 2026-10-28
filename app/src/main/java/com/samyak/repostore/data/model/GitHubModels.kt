package com.samyak.repostore.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.samyak.repostore.R

data class GitHubSearchResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("incomplete_results") val incompleteResults: Boolean,
    val items: List<GitHubRepo>
)

@Entity(tableName = "repositories")
data class GitHubRepo(
    @PrimaryKey val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stars: Int,
    @SerializedName("forks_count") val forks: Int,
    val language: String?,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("created_at") val createdAt: String,
    val archived: Boolean,
    val owner: Owner,
    val topics: List<String>?,
    @SerializedName("default_branch") val defaultBranch: String?
) {
    data class Owner(
        val login: String,
        @SerializedName("avatar_url") val avatarUrl: String,
        @SerializedName("html_url") val htmlUrl: String
    )
}

data class GitHubRelease(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerializedName("download_count") val downloadCount: Int,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String,
    // GitHub provides the asset checksum as "sha256:<hex>" (available on newer releases).
    // Used to look up the file's security report on VirusTotal without downloading it.
    val digest: String? = null
) {
    /** The raw SHA-256 hex hash without the "sha256:" prefix, or null if unavailable. */
    val sha256: String?
        get() = digest?.substringAfter("sha256:", "")?.takeIf { it.isNotBlank() }
}

data class ReadmeResponse(
    val content: String,
    val encoding: String
)

// GitHub Contents API response
data class GitHubContent(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    @SerializedName("download_url") val downloadUrl: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val size: Long?
)

// UI Models
data class AppItem(
    val repo: GitHubRepo,
    val latestRelease: GitHubRelease?,
    val tag: AppTag?,
    val iconUrls: List<String> = emptyList()
)

enum class AppTag {
    NEW, UPDATED, ARCHIVED
}



enum class AppCategory(@param:androidx.annotation.StringRes val titleRes: Int, val displayName: String, val queries: List<String>, val iconRes: Int, val colorRes: Int) {
    ALL(R.string.cat_name_all, "All", listOf("android app", "topic:android"), R.drawable.ic_apps, R.color.cat_bg_brown),
    AI_CHAT(R.string.cat_name_ai_chat, "AI Chat", listOf("android ai chat", "android chatbot llm", "topic:ai topic:android"), R.drawable.ic_cat_ai, R.color.cat_bg_blue),
    APP_MANAGER(R.string.cat_name_app_manager, "App Manager", listOf("android app manager", "android package manager", "topic:app-manager topic:android"), R.drawable.ic_cat_app_manager, R.color.cat_bg_green),
    APP_STORE_UPDATER(R.string.cat_name_app_store_updater, "App Store & Updater", listOf("android app store", "android app updater fdroid", "topic:app-store topic:android"), R.drawable.ic_update, R.color.cat_bg_green),
    BATTERY(R.string.cat_name_battery, "Battery", listOf("battery topic:android", "android battery saver", "topic:battery topic:android"), R.drawable.ic_cat_battery, R.color.cat_bg_green),
    BOOKMARK(R.string.cat_name_bookmark, "Bookmark", listOf("bookmark topic:android", "android bookmark manager", "android bookmarks", "topic:bookmark topic:android"), R.drawable.ic_cat_bookmark, R.color.cat_bg_yellow),
    BROWSER(R.string.cat_name_browser, "Browser", listOf("android browser", "android webview browser", "topic:browser topic:android"), R.drawable.ic_cat_browser, R.color.cat_bg_blue),
    CALCULATOR(R.string.cat_name_calculator, "Calculator", listOf("android calculator", "android scientific calculator", "topic:calculator topic:android"), R.drawable.ic_cat_calculator, R.color.cat_bg_indigo),
    CALENDAR_AGENDA(R.string.cat_name_calendar_agenda, "Calendar & Agenda", listOf("android calendar", "android agenda planner", "topic:calendar topic:android"), R.drawable.ic_cat_calendar, R.color.cat_bg_indigo),
    CLOCK(R.string.cat_name_clock, "Clock", listOf("android clock", "android alarm timer", "topic:clock topic:android"), R.drawable.ic_cat_clock, R.color.cat_bg_indigo),
    CLOUD_STORAGE(R.string.cat_name_cloud_storage, "Cloud Storage & File Sync", listOf("android cloud storage", "android file sync", "topic:cloud-storage topic:android"), R.drawable.ic_cat_cloud, R.color.cat_bg_blue),
    CONNECTIVITY(R.string.cat_name_connectivity, "Connectivity", listOf("android connectivity", "android wifi bluetooth", "topic:connectivity topic:android"), R.drawable.ic_cat_connect, R.color.cat_bg_blue),
    CONTACT(R.string.cat_name_contact, "Contact", listOf("android contacts", "android contact manager", "topic:contacts topic:android"), R.drawable.ic_cat_contact, R.color.cat_bg_pink),
    DEVELOPMENT(R.string.cat_name_development, "Development", listOf("android ide development", "android code editor", "topic:development topic:android"), R.drawable.ic_code, R.color.cat_bg_teal),
    DNS_HOSTS(R.string.cat_name_dns_hosts, "DNS & Hosts", listOf("android dns", "android hosts blocker", "topic:dns topic:android"), R.drawable.ic_cat_dns, R.color.cat_bg_blue),
    DRAW(R.string.cat_name_draw, "Draw", listOf("drawing topic:android", "android drawing app", "topic:drawing topic:android"), R.drawable.ic_cat_image, R.color.cat_bg_yellow),
    EBOOK_READER(R.string.cat_name_ebook_reader, "eBook Reader", listOf("android ebook reader", "android epub reader", "topic:ebook-reader topic:android"), R.drawable.ic_cat_book, R.color.cat_bg_cyan),
    EMAIL(R.string.cat_name_email, "Email", listOf("email topic:android", "android email client", "android mail", "topic:email topic:android"), R.drawable.ic_cat_email, R.color.cat_bg_blue),
    FILE_ENCRYPTION(R.string.cat_name_file_encryption, "File Encryption & Vault", listOf("android file encryption", "android vault", "topic:encryption topic:android"), R.drawable.ic_cat_security, R.color.cat_bg_red),
    FILE_TRANSFER(R.string.cat_name_file_transfer, "File Transfer", listOf("android file transfer", "android file sharing", "topic:file-transfer topic:android"), R.drawable.ic_download, R.color.cat_bg_blue),
    FIREWALL(R.string.cat_name_firewall, "Firewall", listOf("android firewall", "android network firewall", "topic:firewall topic:android"), R.drawable.ic_cat_security, R.color.cat_bg_red),
    FINANCE_MANAGER(R.string.cat_name_finance_manager, "Finance Manager", listOf("android finance manager", "android expense tracker", "topic:finance topic:android"), R.drawable.ic_cat_finance, R.color.cat_bg_green),
    FLASHLIGHT(R.string.cat_name_flashlight, "Flashlight", listOf("android flashlight", "android torch", "topic:flashlight topic:android"), R.drawable.ic_cat_flashlight, R.color.cat_bg_yellow),
    FORUM(R.string.cat_name_forum, "Forum", listOf("android forum client", "android reddit client", "topic:forum topic:android"), R.drawable.ic_cat_message, R.color.cat_bg_orange),
    GALLERY(R.string.cat_name_gallery, "Gallery", listOf("android gallery", "android photo gallery", "topic:gallery topic:android"), R.drawable.ic_cat_image, R.color.cat_bg_yellow),
    GAMES(R.string.cat_name_games, "Games", listOf("topic:android topic:game", "android game", "topic:android-game"), R.drawable.ic_games, R.color.cat_bg_purple),
    GRAPHICS(R.string.cat_name_graphics, "Graphics", listOf("android graphics editor", "android image editor", "topic:graphics topic:android"), R.drawable.ic_cat_image, R.color.cat_bg_yellow),
    HABIT_TRACKER(R.string.cat_name_habit_tracker, "Habit Tracker", listOf("android habit tracker", "android daily tracker", "topic:habit-tracker topic:android"), R.drawable.ic_cat_habit, R.color.cat_bg_indigo),
    ICON_PACK(R.string.cat_name_icon_pack, "Icon Pack", listOf("android icon pack", "android icons theme", "topic:icon-pack topic:android"), R.drawable.ic_cat_icon_pack, R.color.cat_bg_yellow),
    INTERNET(R.string.cat_name_internet, "Internet", listOf("android internet tools", "android network tools", "topic:internet topic:android"), R.drawable.ic_cat_connect, R.color.cat_bg_blue),
    INVENTORY(R.string.cat_name_inventory, "Inventory", listOf("android inventory", "android inventory manager", "topic:inventory topic:android"), R.drawable.ic_cat_inventory, R.color.cat_bg_indigo),
    KEYBOARD_IME(R.string.cat_name_keyboard_ime, "Keyboard & IME", listOf("android keyboard", "android ime", "topic:keyboard topic:android"), R.drawable.ic_cat_keyboard, R.color.cat_bg_indigo),
    LAUNCHER(R.string.cat_name_launcher, "Launcher", listOf("android launcher", "android home screen", "topic:launcher topic:android"), R.drawable.ic_cat_launcher, R.color.cat_bg_indigo),
    LOCAL_MEDIA_PLAYER(R.string.cat_name_local_media_player, "Local Media Player", listOf("android media player", "android video player local", "topic:media-player topic:android"), R.drawable.ic_cat_video, R.color.cat_bg_purple),
    LOCATION_TRACKER(R.string.cat_name_location_tracker, "Location Tracker & Sharing", listOf("android location tracker", "android gps tracker", "topic:location topic:android"), R.drawable.ic_location, R.color.cat_bg_lime),
    MESSAGING(R.string.cat_name_messaging, "Messaging", listOf("android messaging", "android sms messenger", "topic:messaging topic:android"), R.drawable.ic_cat_message, R.color.cat_bg_orange),
    MONEY(R.string.cat_name_money, "Money", listOf("android money manager", "android budget", "topic:money topic:android"), R.drawable.ic_cat_finance, R.color.cat_bg_green),
    MULTIMEDIA(R.string.cat_name_multimedia, "Multimedia", listOf("android multimedia", "android media app", "topic:multimedia topic:android"), R.drawable.ic_cat_video, R.color.cat_bg_purple),
    MUSIC_AUDIO(R.string.cat_name_music_audio, "Music & Audio", listOf("android music player", "android audio player", "topic:music topic:android"), R.drawable.ic_cat_music, R.color.cat_bg_purple),
    VIDEO_PLAYERS(R.string.cat_name_video_players, "Video Players", listOf("android video player", "android media player video", "topic:video-player topic:android"), R.drawable.ic_cat_video, R.color.cat_bg_purple),
    MUSIC_PRACTICE(R.string.cat_name_music_practice, "Music Practice Tool", listOf("android music practice", "android metronome tuner", "topic:music-practice topic:android"), R.drawable.ic_cat_music, R.color.cat_bg_purple),
    NAVIGATION(R.string.cat_name_navigation, "Navigation", listOf("android navigation", "android maps navigation", "topic:navigation topic:android"), R.drawable.ic_cat_navigation, R.color.cat_bg_lime),
    NETWORK_ANALYZER(R.string.cat_name_network_analyzer, "Network Analyzer", listOf("android network analyzer", "android wifi analyzer", "topic:network-analyzer topic:android"), R.drawable.ic_cat_connect, R.color.cat_bg_blue),
    NEWS(R.string.cat_name_news, "News", listOf("android news reader", "android rss news", "topic:news topic:android"), R.drawable.ic_cat_news, R.color.cat_bg_cyan),
    NOTE(R.string.cat_name_note, "Note", listOf("android notes", "android note taking", "topic:notes topic:android"), R.drawable.ic_cat_productivity, R.color.cat_bg_indigo),
    ONLINE_MEDIA_PLAYER(R.string.cat_name_online_media_player, "Online Media Player", listOf("android streaming player", "android online video", "topic:streaming topic:android"), R.drawable.ic_cat_video, R.color.cat_bg_purple),
    PASS_WALLET(R.string.cat_name_pass_wallet, "Pass Wallet", listOf("android pass wallet", "android boarding pass", "topic:wallet topic:android"), R.drawable.ic_cat_finance, R.color.cat_bg_green),
    PASSWORD_2FA(R.string.cat_name_fa, "Password & 2FA", listOf("android password manager", "android 2fa authenticator", "topic:password-manager topic:android"), R.drawable.ic_cat_security, R.color.cat_bg_red),
    PHONE_SMS(R.string.cat_name_phone_sms, "Phone & SMS", listOf("android phone dialer", "android sms app", "topic:dialer topic:android"), R.drawable.ic_cat_message, R.color.cat_bg_orange),
    PODCAST(R.string.cat_name_podcast, "Podcast", listOf("android podcast", "android podcast player", "topic:podcast topic:android"), R.drawable.ic_cat_podcast, R.color.cat_bg_orange),
    PUBLIC_TRANSPORT(R.string.cat_name_public_transport, "Public Transport", listOf("android public transport", "android transit app", "topic:public-transport topic:android"), R.drawable.ic_cat_transport, R.color.cat_bg_lime),
    RADIO(R.string.cat_name_radio, "Radio", listOf("android radio", "android fm radio", "topic:radio topic:android"), R.drawable.ic_cat_radio, R.color.cat_bg_purple),
    READING(R.string.cat_name_reading, "Reading", listOf("android reading app", "android reader", "topic:reading topic:android"), R.drawable.ic_cat_book, R.color.cat_bg_cyan),
    RECIPE_MANAGER(R.string.cat_name_recipe_manager, "Recipe Manager", listOf("android recipe manager", "android cookbook", "topic:recipe topic:android"), R.drawable.ic_cat_recipe, R.color.cat_bg_pink),
    REMOTE_CONTROLLER(R.string.cat_name_remote_controller, "Remote Controller", listOf("android remote control", "android tv remote", "topic:remote-control topic:android"), R.drawable.ic_cat_remote, R.color.cat_bg_indigo),
    SCIENCE_EDUCATION(R.string.cat_name_science_education, "Science & Education", listOf("android education", "android science learning", "topic:education topic:android"), R.drawable.ic_cat_science, R.color.cat_bg_teal),
    SECURITY(R.string.cat_name_security, "Security", listOf("android security", "android antivirus privacy", "topic:security topic:android"), R.drawable.ic_cat_security, R.color.cat_bg_red),
    SHOPPING_LIST(R.string.cat_name_shopping_list, "Shopping List", listOf("android shopping list", "android grocery list", "topic:shopping-list topic:android"), R.drawable.ic_cat_shopping, R.color.cat_bg_pink),
    SOCIAL_NETWORK(R.string.cat_name_social_network, "Social Network", listOf("android social network", "android mastodon client", "topic:social-network topic:android"), R.drawable.ic_cat_message, R.color.cat_bg_orange),
    SPORTS_HEALTH(R.string.cat_name_sports_health, "Sports & Health", listOf("android health fitness", "android sports tracker", "topic:health topic:android"), R.drawable.ic_cat_productivity, R.color.cat_bg_pink),
    STREAMING_APP(R.string.cat_name_streaming_app, "Streaming App", listOf("repo:libre-tube/libretube", "streaming topic:android", "streaming-app topic:android"), R.drawable.ic_cat_video, R.color.cat_bg_purple),
    SYSTEM(R.string.cat_name_system, "System", listOf("android system tool", "android root tool", "topic:system topic:android"), R.drawable.ic_settings, R.color.cat_bg_green),
    TASK(R.string.cat_name_task, "Task", listOf("android task manager", "android todo list", "topic:todo topic:android"), R.drawable.ic_cat_todo, R.color.cat_bg_indigo),
    TEXT_EDITOR(R.string.cat_name_text_editor, "Text Editor", listOf("android text editor", "android code editor text", "topic:text-editor topic:android"), R.drawable.ic_code, R.color.cat_bg_teal),
    THEMING(R.string.cat_name_theming, "Theming", listOf("android theming", "android theme engine", "topic:theming topic:android"), R.drawable.ic_cat_icon_pack, R.color.cat_bg_yellow),
    TIME(R.string.cat_name_time, "Time", listOf("android time tracker", "android pomodoro timer", "topic:time-tracker topic:android"), R.drawable.ic_cat_clock, R.color.cat_bg_indigo),
    TOOLS(R.string.cat_name_tools, "Tools", listOf("android tool", "android utility", "topic:android-tool"), R.drawable.ic_cat_tool, R.color.cat_bg_green),
    TRANSLATION_DICTIONARY(R.string.cat_name_translation_dictionary, "Translation & Dictionary", listOf("android translation", "android dictionary", "topic:translation topic:android"), R.drawable.ic_cat_translation, R.color.cat_bg_cyan),
    UNIT_CONVERTOR(R.string.cat_name_unit_convertor, "Unit Converter", listOf("android unit converter", "android converter", "topic:unit-converter topic:android"), R.drawable.ic_cat_convert, R.color.cat_bg_indigo),
    VOICE_VIDEO_CHAT(R.string.cat_name_voice_video_chat, "Voice & Video Chat", listOf("android voice chat", "android video call", "topic:voip topic:android"), R.drawable.ic_cat_video_call, R.color.cat_bg_orange),
    VPN(R.string.cat_name_vpn, "VPN & Proxy", listOf("vpn topic:android", "android vpn", "proxy topic:android", "android proxy", "v2ray topic:android", "shadowsocks topic:android", "wireguard topic:android", "topic:vpn topic:android", "repo:ProtonVPN/android-app"), R.drawable.ic_cat_vpn, R.color.cat_bg_blue),
    WALLET(R.string.cat_name_wallet, "Wallet", listOf("android wallet", "android crypto wallet", "topic:wallet topic:android"), R.drawable.ic_cat_finance, R.color.cat_bg_green),
    WALLPAPER(R.string.cat_name_wallpaper, "Wallpaper", listOf("android wallpaper", "android live wallpaper", "topic:wallpaper topic:android"), R.drawable.ic_cat_image, R.color.cat_bg_yellow),
    WEATHER(R.string.cat_name_weather, "Weather", listOf("android weather", "android weather app", "topic:weather topic:android"), R.drawable.ic_cat_weather, R.color.cat_bg_pink),
    WORKOUT(R.string.cat_name_workout, "Workout", listOf("android workout", "android exercise fitness", "topic:workout topic:android"), R.drawable.ic_cat_productivity, R.color.cat_bg_pink),
    WRITING(R.string.cat_name_writing, "Writing", listOf("android writing", "android markdown editor", "topic:writing topic:android"), R.drawable.ic_code, R.color.cat_bg_teal),
    PRODUCTIVITY(R.string.cat_name_productivity, "Productivity", listOf("android productivity", "android notes", "android todo", "topic:android-productivity"), R.drawable.ic_cat_productivity, R.color.cat_bg_indigo),
    OPEN_SOURCE(R.string.cat_name_open_source, "Open Source", listOf("android foss", "android open-source", "topic:foss topic:android"), R.drawable.ic_cat_foss, R.color.cat_bg_brown),
    TRENDING(R.string.cat_name_trending, "Trending", listOf("repo:libre-tube/libretube", "android app", "topic:android", "android apk"), R.drawable.ic_star, R.color.cat_bg_orange);

    // For backward compatibility
    val query: String get() = queries.first()
}

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("html_url") val htmlUrl: String,
    val bio: String?,
    val company: String?,
    val location: String?,
    val email: String?,
    val blog: String?,
    @SerializedName("twitter_username") val twitterUsername: String?,
    @SerializedName("public_repos") val publicRepos: Int,
    @SerializedName("public_gists") val publicGists: Int,
    val followers: Int,
    val following: Int,
    @SerializedName("created_at") val createdAt: String
)
