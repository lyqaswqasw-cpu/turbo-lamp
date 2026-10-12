package com.example.ui.publisher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.Channel
import com.example.data.model.ChannelCategory
import com.example.data.model.ChannelServer
import com.example.data.model.XtremeAccount
import com.example.data.model.XtremeItem
import com.example.data.util.ChannelClusteringEngine
import com.example.data.util.M3uParser
import com.example.data.xtreme.XtremeApiClient
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.BrightLavender
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.DarkPurpleContainer
import com.example.ui.theme.ElectricGold
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LiveRed
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PitchGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividViolet
import kotlinx.coroutines.launch

private enum class CategorySourceMode(val label: String) {
    NONE("بدون مصدر (يدوي / اختياري)"),
    M3U("رابط قائمة M3U"),
    XTREME("حساب Xtream Codes")
}

@Composable
fun CategoryPublisherTab(
    categories: List<ChannelCategory>,
    xtremeAccounts: List<XtremeAccount> = emptyList(),
    onSaveCategory: (ChannelCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveChannel: (Channel) -> Unit = {},
    onSaveBatchChannels: (List<Channel>, String) -> Unit = { _, _ -> },
    onPlayChannelPreview: (Channel, ChannelServer?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val xtremeClient = remember { XtremeApiClient() }

    var editingId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var orderText by remember { mutableStateOf("0") }
    var sourceMode by remember { mutableStateOf(CategorySourceMode.XTREME) }

    // M3U Source
    var m3uUrl by remember { mutableStateOf("") }

    // Xtreme Source credentials
    var xtremeHost by remember { mutableStateOf("") }
    var xtremeUsername by remember { mutableStateOf("") }
    var xtremePassword by remember { mutableStateOf("") }
    var xtremeExtension by remember { mutableStateOf("m3u8") }

    // Xtreme Classifications / Categories fetched from the Xtreme server
    var xtremeCategoriesList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedXtremeCategoryId by remember { mutableStateOf("") }
    var selectedXtremeCategoryName by remember { mutableStateOf("") }
    var isFetchingCategories by remember { mutableStateOf(false) }
    var categoriesFetchError by remember { mutableStateOf<String?>(null) }

    // Quality Merging Choice: (دمج الجودات كسيرفرات في الأعلى) vs (فقط قناة من دون دمج)
    var isMergedQualitiesMode by remember { mutableStateOf(true) }

    // Cached raw items to allow instantly switching between Merged and Unmerged modes without re-fetching
    var rawLiveItems by remember { mutableStateOf<List<XtremeItem>>(emptyList()) }
    var rawM3uList by remember { mutableStateOf<List<Channel>>(emptyList()) }

    // Processed channels for preview & publishing
    var previewChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var rawStreamsCount by remember { mutableIntStateOf(0) }
    var isFetchingPreview by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var publishBatchFeedback by remember { mutableStateOf<String?>(null) }
    var channelSearchQuery by remember { mutableStateOf("") }
    var selectedPreviewChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedPreviewServerId by remember { mutableStateOf<String?>(null) }

    // Helper: recalculates previewChannels from raw cached items based on merge mode
    fun applyMergeModeToCurrentStreams(merged: Boolean, targetCatName: String) {
        val catName = targetCatName.ifBlank { name.trim() }.ifBlank { selectedXtremeCategoryName }.ifBlank { "عام" }
        if (rawLiveItems.isNotEmpty()) {
            if (merged) {
                val clustered = ChannelClusteringEngine.clusterXtremeLiveItems(
                    items = rawLiveItems,
                    forcedCategoryName = catName
                )
                previewChannels = clustered
                selectedPreviewChannel = clustered.firstOrNull()
                selectedPreviewServerId = clustered.firstOrNull()?.servers?.firstOrNull()?.id
            } else {
                val unmerged = ChannelClusteringEngine.toUnmergedChannels(
                    items = rawLiveItems,
                    forcedCategoryName = catName
                )
                previewChannels = unmerged
                selectedPreviewChannel = unmerged.firstOrNull()
                selectedPreviewServerId = unmerged.firstOrNull()?.servers?.firstOrNull()?.id
            }
        } else if (rawM3uList.isNotEmpty()) {
            if (merged) {
                previewChannels = rawM3uList
                selectedPreviewChannel = rawM3uList.firstOrNull()
                selectedPreviewServerId = rawM3uList.firstOrNull()?.servers?.firstOrNull()?.id
            } else {
                // Flatten servers into separate channels
                val unmergedList = rawM3uList.flatMap { ch ->
                    if (ch.servers.size <= 1) listOf(ch)
                    else ch.servers.mapIndexed { idx, srv ->
                        ch.copy(
                            id = "${ch.id}_s_${idx + 1}",
                            name = "${ch.name} (${srv.name})",
                            servers = listOf(srv)
                        )
                    }
                }
                previewChannels = unmergedList
                selectedPreviewChannel = unmergedList.firstOrNull()
                selectedPreviewServerId = unmergedList.firstOrNull()?.servers?.firstOrNull()?.id
            }
        }
    }

    // Loads classifications/categories from the Xtreme account
    fun loadXtremeCategories(host: String, user: String, pass: String) {
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            categoriesFetchError = "يرجى ملء بيانات السيرفر واسم المستخدم وكلمة المرور أولاً"
            return
        }
        coroutineScope.launch {
            isFetchingCategories = true
            categoriesFetchError = null
            try {
                val tempAcc = XtremeAccount(
                    name = "حساب Xtreme",
                    host = host.trim(),
                    username = user.trim(),
                    password = pass.trim()
                )
                val list = xtremeClient.fetchLiveCategoriesList(tempAcc)
                xtremeCategoriesList = list
                if (list.isEmpty()) {
                    categoriesFetchError = "لم يتم العثور على تصنيفات أو تعذر الاتصال بالسيرفر. تأكد من البيانات."
                } else {
                    categoriesFetchError = null
                }
            } catch (e: Exception) {
                categoriesFetchError = "فشل جلب التصنيفات: ${e.localizedMessage ?: "تأكد من الاتصال"}"
            } finally {
                isFetchingCategories = false
            }
        }
    }

    // Fetches live stream channels for the chosen category and applies the merge choice
    fun fetchChannelsForSelectedCategory(
        targetCategoryName: String,
        mode: CategorySourceMode,
        targetM3u: String,
        host: String,
        user: String,
        pass: String,
        ext: String,
        targetCatId: String,
        mergedQualities: Boolean
    ) {
        coroutineScope.launch {
            isFetchingPreview = true
            previewError = null
            publishBatchFeedback = null
            try {
                when (mode) {
                    CategorySourceMode.XTREME -> {
                        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
                            previewError = "يرجى إدخال بيانات حساب Xtream (السيرفر واسم المستخدم وكلمة المرور)"
                        } else {
                            val tempAcc = XtremeAccount(
                                name = targetCategoryName.ifBlank { "حساب Xtreme" },
                                host = host.trim(),
                                username = user.trim(),
                                password = pass.trim(),
                                streamExtension = ext.ifBlank { "m3u8" }
                            )
                            val liveItems = xtremeClient.fetchLiveStreams(tempAcc, targetCatId)
                            rawLiveItems = liveItems
                            rawStreamsCount = liveItems.size
                            if (liveItems.isEmpty()) {
                                previewError = "لم يتم العثور على قنوات بث مباشر في هذا التصنيف"
                                previewChannels = emptyList()
                            } else {
                                applyMergeModeToCurrentStreams(mergedQualities, targetCategoryName)
                            }
                        }
                    }
                    CategorySourceMode.M3U -> {
                        if (targetM3u.isBlank()) {
                            previewError = "يرجى إدخال رابط M3U أولاً لعرض القنوات"
                        } else {
                            val parsed = M3uParser.parse(targetM3u.trim(), targetCategoryName.ifBlank { "عام" })
                            rawM3uList = parsed
                            rawStreamsCount = parsed.sumOf { it.servers.size }
                            if (parsed.isEmpty()) {
                                previewError = "لم يتم العثور على قنوات في رابط M3U المحدد"
                                previewChannels = emptyList()
                            } else {
                                applyMergeModeToCurrentStreams(mergedQualities, targetCategoryName)
                            }
                        }
                    }
                    CategorySourceMode.NONE -> {
                        previewChannels = emptyList()
                        rawStreamsCount = 0
                    }
                }
            } catch (e: Exception) {
                previewError = "خطأ أثناء جلب القنوات: ${e.localizedMessage ?: "تأكد من الاتصال"}"
            } finally {
                isFetchingPreview = false
            }
        }
    }

    val filteredPreviewChannels = remember(previewChannels, channelSearchQuery) {
        val q = channelSearchQuery.trim()
        if (q.isBlank()) {
            previewChannels
        } else {
            previewChannels.filter { ch ->
                ch.name.contains(q, ignoreCase = true) ||
                    ch.category.contains(q, ignoreCase = true) ||
                    ch.servers.any { srv -> srv.name.contains(q, ignoreCase = true) }
            }
        }
    }

    // Active channel for the top featured merged servers bar
    val topFeaturedChannel = remember(selectedPreviewChannel, filteredPreviewChannels, channelSearchQuery) {
        if (selectedPreviewChannel != null && filteredPreviewChannels.any { it.id == selectedPreviewChannel?.id }) {
            selectedPreviewChannel
        } else if (channelSearchQuery.isNotBlank() && filteredPreviewChannels.isNotEmpty()) {
            filteredPreviewChannels.first()
        } else {
            selectedPreviewChannel ?: filteredPreviewChannels.firstOrNull { it.servers.size > 1 } ?: filteredPreviewChannels.firstOrNull()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // =========================================================================
        // 1. CATEGORY CREATION & CLASSIFICATION PUBLISHER CARD
        // =========================================================================
        item {
            Surface(
                color = CardSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingId.isBlank()) "نشر قسم قنوات من تصنيف حساب Xtreme / M3U" else "تعديل القسم المحدد",
                            color = ElectricGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Surface(
                            color = PitchGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, PitchGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "متصل أونلاين",
                                color = PitchGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم القسم في التطبيق (مثال: beIN SPORTS أو دوري الأبطال)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PitchGreen,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("category_name_input")
                        )

                        OutlinedTextField(
                            value = orderText,
                            onValueChange = { orderText = it },
                            label = { Text("الترتيب") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.weight(0.3f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SOURCE MODE SELECTOR
                    Text(
                        text = "اختر مصدر جلب القنوات لهذا القسم:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategorySourceMode.values().forEach { mode ->
                            val isSelected = sourceMode == mode
                            val icon = when (mode) {
                                CategorySourceMode.NONE -> Icons.Default.Category
                                CategorySourceMode.M3U -> Icons.Default.Link
                                CategorySourceMode.XTREME -> Icons.Default.Dns
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) ElectricPurple.copy(alpha = 0.28f) else CardSurface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) NeonPurple else TextMuted.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { sourceMode = mode }
                                    .testTag("category_source_mode_${mode.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) ElectricGold else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // SOURCE MODE 1: NONE (MANUAL)
                    AnimatedVisibility(visible = sourceMode == CategorySourceMode.NONE) {
                        Surface(
                            color = CardSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .border(1.dp, TextMuted.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = "سيتم إنشاء هذا القسم يدوياً بدون ربطه برابط خارجي، ويمكنك إضافة القنوات إليه بشكل مباشر من تبويب القنوات.",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // SOURCE MODE 2: M3U PLAYLIST URL
                    AnimatedVisibility(visible = sourceMode == CategorySourceMode.M3U) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = m3uUrl,
                                onValueChange = { m3uUrl = it },
                                label = { Text("رابط قائمة M3U للقسم") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_m3u_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Quality Merging Toggle for M3U
                            Text(
                                text = "خيارات جودات القنوات والسيرفرات:",
                                color = ElectricGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isMergedQualitiesMode) PitchGreen.copy(alpha = 0.2f) else CardSurface,
                                    border = BorderStroke(1.dp, if (isMergedQualitiesMode) PitchGreen else TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            isMergedQualitiesMode = true
                                            applyMergeModeToCurrentStreams(true, name)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = if (isMergedQualitiesMode) PitchGreen else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "دمج الجودات كسيرفرات في الأعلى",
                                            color = if (isMergedQualitiesMode) Color.White else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isMergedQualitiesMode) AccentCyan.copy(alpha = 0.2f) else CardSurface,
                                    border = BorderStroke(1.dp, if (!isMergedQualitiesMode) AccentCyan else TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            isMergedQualitiesMode = false
                                            applyMergeModeToCurrentStreams(false, name)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = if (!isMergedQualitiesMode) AccentCyan else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "فقط قناة من دون دمج (منفصلة)",
                                            color = if (!isMergedQualitiesMode) Color.White else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    fetchChannelsForSelectedCategory(
                                        targetCategoryName = name,
                                        mode = CategorySourceMode.M3U,
                                        targetM3u = m3uUrl,
                                        host = "",
                                        user = "",
                                        pass = "",
                                        ext = "m3u8",
                                        targetCatId = "",
                                        mergedQualities = isMergedQualitiesMode
                                    )
                                },
                                border = BorderStroke(1.dp, AccentCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMergedQualitiesMode) "جلب قنوات M3U ودمج الجودات كسيرفرات" else "جلب قنوات M3U بدون دمج (قنوات منفردة)",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // SOURCE MODE 3: XTREME CODES ACCOUNT & CLASSIFICATIONS
                    AnimatedVisibility(visible = sourceMode == CategorySourceMode.XTREME) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // Quick fill from existing Xtreme accounts
                            if (xtremeAccounts.isNotEmpty()) {
                                Text(
                                    text = "اختيار سريع من الحسابات المحفوظة:",
                                    color = BrightLavender,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(xtremeAccounts, key = { it.id }) { acc ->
                                        val isCurrentAcc = xtremeHost == acc.host && xtremeUsername == acc.username
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrentAcc) PitchGreen.copy(alpha = 0.22f) else CardSurface,
                                            border = BorderStroke(1.dp, if (isCurrentAcc) PitchGreen else TextMuted.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    xtremeHost = acc.host
                                                    xtremeUsername = acc.username
                                                    xtremePassword = acc.password
                                                    xtremeExtension = acc.streamExtension.ifBlank { "m3u8" }
                                                    if (name.isBlank()) name = acc.name
                                                    loadXtremeCategories(acc.host, acc.username, acc.password)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Dns, contentDescription = null, tint = ElectricGold, modifier = Modifier.size(13.dp))
                                                Text(acc.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            OutlinedTextField(
                                value = xtremeHost,
                                onValueChange = { xtremeHost = it },
                                label = { Text("عنوان سيرفر Xtreme (مثال: http://server.com:8080)") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricGold,
                                    unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_xtreme_host_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = xtremeUsername,
                                    onValueChange = { xtremeUsername = it },
                                    label = { Text("اسم المستخدم (Username)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricGold,
                                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("category_xtreme_user_input")
                                )

                                OutlinedTextField(
                                    value = xtremePassword,
                                    onValueChange = { xtremePassword = it },
                                    label = { Text("كلمة المرور (Password)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricGold,
                                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("category_xtreme_pass_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("امتداد البث:", color = TextSecondary, fontSize = 11.5.sp)
                                listOf("m3u8", "ts").forEach { ext ->
                                    val selected = xtremeExtension.equals(ext, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) AccentCyan.copy(alpha = 0.22f) else CardSurface,
                                        border = BorderStroke(1.dp, if (selected) AccentCyan else TextMuted.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable { xtremeExtension = ext }
                                    ) {
                                        Text(
                                            text = ext.uppercase(),
                                            color = if (selected) AccentCyan else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1. BUTTON TO FETCH CLASSIFICATIONS/CATEGORIES FROM XTREME
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        loadXtremeCategories(xtremeHost, xtremeUsername, xtremePassword)
                                    },
                                    border = BorderStroke(1.dp, ElectricGold),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("fetch_xtreme_categories_button")
                                ) {
                                    if (isFetchingCategories) {
                                        CircularProgressIndicator(color = ElectricGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("جاري جلب التصنيفات...", color = ElectricGold, fontSize = 11.5.sp)
                                    } else {
                                        Icon(Icons.Default.Category, contentDescription = null, tint = ElectricGold, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (xtremeCategoriesList.isNotEmpty()) "تحديث تصنيفات Xtreme (${xtremeCategoriesList.size})" else "جلب أقسام وتصنيفات حساب Xtreme",
                                            color = ElectricGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }

                            if (!categoriesFetchError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = categoriesFetchError ?: "",
                                    color = LiveRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 2. DISPLAY FETCHED XTREME CATEGORIES / CLASSIFICATIONS
                            if (xtremeCategoriesList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "اختر التصنيف / القسم المراد نشره وجلب قنواته (${xtremeCategoriesList.size} تصنيف متوفر):",
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item {
                                        val isAllSelected = selectedXtremeCategoryId.isBlank()
                                        FilterChip(
                                            selected = isAllSelected,
                                            onClick = {
                                                selectedXtremeCategoryId = ""
                                                selectedXtremeCategoryName = "جميع التصنيفات"
                                            },
                                            label = {
                                                Text(
                                                    text = "جميع التصنيفات (كامل الحساب)",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isAllSelected) FontWeight.ExtraBold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = if (isAllSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ElectricGold.copy(alpha = 0.22f),
                                                selectedLabelColor = ElectricGold,
                                                selectedLeadingIconColor = ElectricGold
                                            )
                                        )
                                    }

                                    items(xtremeCategoriesList, key = { it.first }) { (catId, catTitle) ->
                                        val isSelected = selectedXtremeCategoryId == catId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedXtremeCategoryId = catId
                                                selectedXtremeCategoryName = catTitle
                                                if (name.isBlank() || xtremeCategoriesList.any { it.second == name.trim() }) {
                                                    name = catTitle
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = catTitle,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PitchGreen.copy(alpha = 0.22f),
                                                selectedLabelColor = PitchGreen,
                                                selectedLeadingIconColor = PitchGreen
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. QUALITY MERGING SELECTION (دمج الجودات كسيرفرات في الأعلى أو فقط قناة من دون دمج)
                            Surface(
                                color = Color(0xFF0F111E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "طريقة نشر وعرض الجودات والسيرفرات:",
                                        color = ElectricGold,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Choice 1: Merge Qualities into top servers
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isMergedQualitiesMode) PitchGreen.copy(alpha = 0.22f) else CardSurface,
                                            border = BorderStroke(
                                                width = if (isMergedQualitiesMode) 1.5.dp else 1.dp,
                                                color = if (isMergedQualitiesMode) PitchGreen else TextMuted.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    isMergedQualitiesMode = true
                                                    applyMergeModeToCurrentStreams(true, name)
                                                }
                                                .testTag("mode_merge_qualities")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Layers,
                                                        contentDescription = null,
                                                        tint = if (isMergedQualitiesMode) PitchGreen else TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "دمج الجودات تلقائياً",
                                                        color = if (isMergedQualitiesMode) PitchGreen else TextPrimary,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                Text(
                                                    text = "تظهر كسيرفرات في الأعلى (4K, FHD, HD, SD...)",
                                                    color = TextSecondary,
                                                    fontSize = 9.5.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        // Choice 2: Single separate channels without merge
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (!isMergedQualitiesMode) AccentCyan.copy(alpha = 0.22f) else CardSurface,
                                            border = BorderStroke(
                                                width = if (!isMergedQualitiesMode) 1.5.dp else 1.dp,
                                                color = if (!isMergedQualitiesMode) AccentCyan else TextMuted.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    isMergedQualitiesMode = false
                                                    applyMergeModeToCurrentStreams(false, name)
                                                }
                                                .testTag("mode_unmerged_channels")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LiveTv,
                                                        contentDescription = null,
                                                        tint = if (!isMergedQualitiesMode) AccentCyan else TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "قنوات مستقلة (بدون دمج)",
                                                        color = if (!isMergedQualitiesMode) AccentCyan else TextPrimary,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                                Text(
                                                    text = "كل جودة قناة منفردة باسمها المستقل",
                                                    color = TextSecondary,
                                                    fontSize = 9.5.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4. FETCH CHANNELS BUTTON
                            val targetCategoryDisplay = if (selectedXtremeCategoryId.isNotBlank()) {
                                "لتصنيف (${selectedXtremeCategoryName.ifBlank { "المحدد" }})"
                            } else {
                                "لكل تصنيفات الحساب"
                            }
                            val modeDisplay = if (isMergedQualitiesMode) "مع دمج الجودات كسيرفرات" else "بدون دمج (قنوات منفصلة)"

                            Button(
                                onClick = {
                                    fetchChannelsForSelectedCategory(
                                        targetCategoryName = name,
                                        mode = CategorySourceMode.XTREME,
                                        targetM3u = "",
                                        host = xtremeHost,
                                        user = xtremeUsername,
                                        pass = xtremePassword,
                                        ext = xtremeExtension,
                                        targetCatId = selectedXtremeCategoryId,
                                        mergedQualities = isMergedQualitiesMode
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VividViolet,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_xtreme_category_channels_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "جلب قنوات $targetCategoryDisplay $modeDisplay",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SAVE / CANCEL CATEGORY BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingId.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    editingId = ""
                                    name = ""
                                    orderText = "0"
                                    sourceMode = CategorySourceMode.XTREME
                                    m3uUrl = ""
                                    xtremeHost = ""
                                    xtremeUsername = ""
                                    xtremePassword = ""
                                    xtremeExtension = "m3u8"
                                    selectedXtremeCategoryId = ""
                                    selectedXtremeCategoryName = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء التعديل", color = TextSecondary)
                            }
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    val finalM3u = if (sourceMode == CategorySourceMode.M3U) m3uUrl.trim() else ""
                                    val finalHost = if (sourceMode == CategorySourceMode.XTREME) xtremeHost.trim() else ""
                                    val finalUser = if (sourceMode == CategorySourceMode.XTREME) xtremeUsername.trim() else ""
                                    val finalPass = if (sourceMode == CategorySourceMode.XTREME) xtremePassword.trim() else ""

                                    val cat = ChannelCategory(
                                        id = editingId,
                                        name = name.trim(),
                                        order = orderText.toIntOrNull() ?: 0,
                                        m3uUrl = finalM3u,
                                        xtremeHost = finalHost,
                                        xtremeUsername = finalUser,
                                        xtremePassword = finalPass,
                                        xtremeExtension = xtremeExtension.ifBlank { "m3u8" },
                                        xtremeCategoryId = selectedXtremeCategoryId,
                                        xtremeCategoryName = selectedXtremeCategoryName
                                    )
                                    onSaveCategory(cat)
                                    editingId = ""
                                    name = ""
                                    orderText = "0"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PitchGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("save_category_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (editingId.isBlank()) "حفظ ونشر القسم في التطبيق" else "تحديث القسم",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. LOADING & ERROR BANNERS
        // =========================================================================
        if (isFetchingPreview) {
            item {
                Surface(
                    color = CardSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = ElectricGold, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        Column {
                            Text(
                                text = if (isMergedQualitiesMode) "جاري جلب القنوات ودمج الجودات تلقائياً كسيرفرات..." else "جاري جلب القنوات بشكل منفصل بدون دمج...",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "يتم التحميل بسرعة فائقة مع فرز السيرفرات وجودات 4K / FHD / HD / SD",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (!previewError.isNullOrBlank()) {
            item {
                Surface(
                    color = LiveRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LiveRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewError ?: "",
                        color = LiveRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (!publishBatchFeedback.isNullOrBlank()) {
            item {
                Surface(
                    color = PitchGreen.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PitchGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PitchGreen, modifier = Modifier.size(18.dp))
                        Text(
                            text = publishBatchFeedback ?: "",
                            color = PitchGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 3. CHANNEL BROWSER + BATCH PUBLISH + TOP MERGED QUALITY SERVERS BAR
        // =========================================================================
        if (previewChannels.isNotEmpty()) {
            item {
                Surface(
                    color = CardSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(ElectricGold, AccentCyan, NeonPurple))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMergedQualitiesMode) {
                                        "مستعرض القنوات المدمجة (${previewChannels.size} قناة موحدة)"
                                    } else {
                                        "مستعرض القنوات المنفردة (${previewChannels.size} قناة بدون دمج)"
                                    },
                                    color = ElectricGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isMergedQualitiesMode) {
                                        "تم دمج $rawStreamsCount بث مباشر في ${previewChannels.size} قناة رئيسية مع جوداتها في الأعلى"
                                    } else {
                                        "تم جلب $rawStreamsCount بث مباشر كقنوات مستقلة لكل جودة"
                                    },
                                    color = PitchGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Quick mode switch chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isMergedQualitiesMode) VividViolet else DarkPurpleContainer,
                                border = BorderStroke(1.dp, if (isMergedQualitiesMode) ElectricGold else NeonPurple),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val newMode = !isMergedQualitiesMode
                                        isMergedQualitiesMode = newMode
                                        applyMergeModeToCurrentStreams(newMode, name)
                                    }
                            ) {
                                Text(
                                    text = if (isMergedQualitiesMode) "الوضع: مدمج (اضغط للفصل)" else "الوضع: منفصل (اضغط للدمج)",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            IconButton(onClick = { previewChannels = emptyList() }) {
                                Icon(Icons.Default.Clear, contentDescription = "إغلاق المعاينة", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // BATCH PUBLISH BUTTON (نشر جميع القنوات دفعة واحدة)
                        Button(
                            onClick = {
                                val targetCat = name.trim().ifBlank { selectedXtremeCategoryName }.ifBlank { "عام" }
                                val finalChannelsToPublish = previewChannels.map { ch ->
                                    ch.copy(category = targetCat)
                                }
                                onSaveBatchChannels(finalChannelsToPublish, targetCat)
                                publishBatchFeedback = "تم نشر جميع القنوات (${finalChannelsToPublish.size} قناة) بنجاح في قسم ($targetCat)"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PitchGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("publish_all_channels_batch_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "نشر جميع قنوات هذا التصنيف دفعة واحدة (${previewChannels.size} قناة)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Search input
                        OutlinedTextField(
                            value = channelSearchQuery,
                            onValueChange = { channelSearchQuery = it },
                            placeholder = { Text("ابحث عن أي قناة بالاسم لعرض جوداتها وسيرفراتها...", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan)
                            },
                            trailingIcon = {
                                if (channelSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { channelSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("category_preview_search_input")
                        )

                        // =========================================================================
                        // TOP MERGED QUALITY SERVERS BAR (السيرفرات تظهر في الأعلى)
                        // =========================================================================
                        if (topFeaturedChannel != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Color(0xFF101322),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, ElectricGold.copy(alpha = 0.65f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.HighQuality,
                                                contentDescription = null,
                                                tint = ElectricGold,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = topFeaturedChannel.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (topFeaturedChannel.servers.size > 1) {
                                                        "السيرفرات والجودات في الأعلى (${topFeaturedChannel.servers.size} جودة متوفرة)"
                                                    } else {
                                                        "سيرفر البث المباشر (جودة وحيدة)"
                                                    },
                                                    color = AccentCyan,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Test play preview button
                                            OutlinedButton(
                                                onClick = {
                                                    val activeSrv = topFeaturedChannel.servers.firstOrNull { it.id == selectedPreviewServerId }
                                                        ?: topFeaturedChannel.servers.firstOrNull()
                                                    onPlayChannelPreview(topFeaturedChannel, activeSrv)
                                                },
                                                border = BorderStroke(1.dp, AccentCyan),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("معاينة", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Publish single channel button
                                            Button(
                                                onClick = {
                                                    val targetCat = name.trim().ifBlank { topFeaturedChannel.category }.ifBlank { selectedXtremeCategoryName }.ifBlank { "عام" }
                                                    val channelToPublish = topFeaturedChannel.copy(category = targetCat)
                                                    onSaveChannel(channelToPublish)
                                                    publishBatchFeedback = "تم نشر قناة (${channelToPublish.name}) بنجاح أونلاين"
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = PitchGreen,
                                                    contentColor = Color.Black
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("نشر هذه القناة", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Merged Servers Row at the top
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(topFeaturedChannel.servers, key = { it.id }) { srv ->
                                            val isSelectedSrv = (selectedPreviewServerId ?: topFeaturedChannel.servers.firstOrNull()?.id) == srv.id
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelectedSrv) VividViolet else CardSurface,
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = if (isSelectedSrv) ElectricGold else NeonPurple.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedPreviewServerId = srv.id }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    if (isSelectedSrv) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(PitchGreen, CircleShape)
                                                        )
                                                    }
                                                    Text(
                                                        text = srv.name,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "نتائج القنوات (${filteredPreviewChannels.size} قناة) — اضغط على أي قناة لعرض جوداتها وسيرفراتها في الأعلى:",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Channel List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 380.dp)
                                .background(CardSurface, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredPreviewChannels, key = { it.id }) { ch ->
                                val isSelected = topFeaturedChannel?.id == ch.id
                                Surface(
                                    color = if (isSelected) ElectricPurple.copy(alpha = 0.22f) else CardSurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) ElectricGold else TextMuted.copy(alpha = 0.18f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedPreviewChannel = ch
                                            selectedPreviewServerId = ch.servers.firstOrNull()?.id
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (ch.imageUrl.isNotBlank()) {
                                                    SubcomposeAsyncImage(
                                                        model = ImageRequest.Builder(LocalContext.current)
                                                            .data(ch.imageUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = ch.name,
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.Black),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.LiveTv,
                                                        contentDescription = null,
                                                        tint = AccentCyan,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = ch.name,
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (ch.servers.size > 1) {
                                                            "${ch.servers.size} سيرفرات جودة مدمجة"
                                                        } else {
                                                            "سيرفر بث وحيد"
                                                        },
                                                        color = PitchGreen,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            // Quality pills
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                ch.servers.take(3).forEach { srv ->
                                                    Surface(
                                                        color = DarkPurpleContainer,
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = BorderStroke(0.6.dp, NeonPurple.copy(alpha = 0.6f))
                                                    ) {
                                                        Text(
                                                            text = srv.name.replace("سيرفر ", ""),
                                                            color = BrightLavender,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (ch.servers.size > 3) {
                                                    Text(
                                                        text = "+${ch.servers.size - 3}",
                                                        color = ElectricGold,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val targetCat = name.trim().ifBlank { ch.category }.ifBlank { selectedXtremeCategoryName }.ifBlank { "عام" }
                                                        onSaveChannel(ch.copy(category = targetCat))
                                                        publishBatchFeedback = "تم نشر قناة (${ch.name}) بنجاح"
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Publish, contentDescription = "نشر القناة", tint = PitchGreen, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 4. EXISTING PUBLISHED CATEGORIES LIST
        // =========================================================================
        item {
            Text(
                text = "الأقسام المنشورة حالياً (${categories.size})",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }

        items(categories, key = { it.id.ifBlank { it.name } }) { cat ->
            val sourceBadgeText = when {
                cat.hasXtremeSource -> {
                    val catLabel = if (cat.xtremeCategoryName.isNotBlank()) " | ${cat.xtremeCategoryName}" else ""
                    "حساب Xtreme (${cat.xtremeUsername})$catLabel"
                }
                cat.hasM3uSource -> "رابط قائمة M3U"
                else -> "قسم يدوي (بدون رابط)"
            }
            val sourceBadgeColor = when {
                cat.hasXtremeSource -> ElectricGold
                cat.hasM3uSource -> AccentCyan
                else -> TextSecondary
            }

            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TextMuted.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when {
                                cat.hasXtremeSource -> Icons.Default.Dns
                                cat.hasM3uSource -> Icons.Default.Link
                                else -> Icons.Default.Category
                            },
                            contentDescription = null,
                            tint = sourceBadgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = cat.name,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "الترتيب: ${cat.order}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "• $sourceBadgeText",
                                    color = sourceBadgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cat.hasXtremeSource || cat.hasM3uSource) {
                            IconButton(
                                onClick = {
                                    name = cat.name
                                    if (cat.hasXtremeSource) {
                                        sourceMode = CategorySourceMode.XTREME
                                        xtremeHost = cat.xtremeHost
                                        xtremeUsername = cat.xtremeUsername
                                        xtremePassword = cat.xtremePassword
                                        xtremeExtension = cat.xtremeExtension
                                        selectedXtremeCategoryId = cat.xtremeCategoryId
                                        selectedXtremeCategoryName = cat.xtremeCategoryName
                                        loadXtremeCategories(cat.xtremeHost, cat.xtremeUsername, cat.xtremePassword)
                                        fetchChannelsForSelectedCategory(
                                            targetCategoryName = cat.name,
                                            mode = CategorySourceMode.XTREME,
                                            targetM3u = "",
                                            host = cat.xtremeHost,
                                            user = cat.xtremeUsername,
                                            pass = cat.xtremePassword,
                                            ext = cat.xtremeExtension,
                                            targetCatId = cat.xtremeCategoryId,
                                            mergedQualities = isMergedQualitiesMode
                                        )
                                    } else {
                                        sourceMode = CategorySourceMode.M3U
                                        m3uUrl = cat.m3uUrl
                                        fetchChannelsForSelectedCategory(
                                            targetCategoryName = cat.name,
                                            mode = CategorySourceMode.M3U,
                                            targetM3u = cat.m3uUrl,
                                            host = "",
                                            user = "",
                                            pass = "",
                                            ext = "m3u8",
                                            targetCatId = "",
                                            mergedQualities = isMergedQualitiesMode
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "عرض وبحث قنوات القسم",
                                    tint = AccentCyan
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                editingId = cat.id
                                name = cat.name
                                orderText = cat.order.toString()
                                m3uUrl = cat.m3uUrl
                                xtremeHost = cat.xtremeHost
                                xtremeUsername = cat.xtremeUsername
                                xtremePassword = cat.xtremePassword
                                xtremeExtension = cat.xtremeExtension.ifBlank { "m3u8" }
                                selectedXtremeCategoryId = cat.xtremeCategoryId
                                selectedXtremeCategoryName = cat.xtremeCategoryName
                                sourceMode = when {
                                    cat.hasXtremeSource -> CategorySourceMode.XTREME
                                    cat.hasM3uSource -> CategorySourceMode.M3U
                                    else -> CategorySourceMode.NONE
                                }
                                if (cat.hasXtremeSource) {
                                    loadXtremeCategories(cat.xtremeHost, cat.xtremeUsername, cat.xtremePassword)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل القسم",
                                tint = ElectricGold
                            )
                        }
                        IconButton(
                            onClick = { onDeleteCategory(cat.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف القسم",
                                tint = LiveRed
                            )
                        }
                    }
                }
            }
        }
    }
}
