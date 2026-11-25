package fr.familia.app.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A photo of Unsplash (règle 19): its id, the name of its file on the images server of Unsplash, and
 * who took it. Shown from Unsplash itself, never copied into the app or the public repository
 * (DESIGN-159 B); Coil keeps it in its disk cache, so it is downloaded once per phone and still shows
 * offline ([prefetchThemePhotos] fetches them all at the start).
 */
data class ThemePhoto(val id: String, val file: String, val author: String) {
    /** The page of the photo, for « Crédits des photos ». */
    val page: String get() = "https://unsplash.com/photos/$id"

    /**
     * The picture itself, 1080 px wide in JPEG: enough for a phone, light on the data plan. Straight
     * from images.unsplash.com, the hotlink Unsplash asks for: the « /download » link of the page is
     * for browsers only and gave no photo in the app (retour d'essai, APK 0.2.618).
     */
    val url: String get() = "https://images.unsplash.com/$file?w=1080&q=80&fm=jpg&fit=max"
}

/** The photo of each theme: the one table of the photos (règle 17: data, added without touching the screens). */
object ThemePhotos {
    private val photos: Map<FamiliaThemeId, ThemePhoto> = mapOf(
        FamiliaThemeId.Clouds to ThemePhoto("KOJ3CIrt1j8", "photo-1516366353199-ed694fec414f", "Fabrizio Conti"),
        FamiliaThemeId.Alps to ThemePhoto("qAD0TbXt5Ys", "photo-1783586856710-18073cc55d4d", "Daniel J. Schwarz"),
        FamiliaThemeId.Sunset to ThemePhoto("uQDRDqpYJHI", "photo-1559494007-9f5847c49d94", "Cristofer Maximilian"),
        FamiliaThemeId.Christmas to ThemePhoto("1PWlPce_ng4", "photo-1768209911442-770f3df80dc2", "Vitalijs Barilo"),
        FamiliaThemeId.Autumn to ThemePhoto("kP-LMhUp4Sw", "photo-1759675001595-7b1e5ff997f0", "Nenad Radojčić"),
        FamiliaThemeId.Lake to ThemePhoto("lpjb_UMOyx8", "photo-1439853949127-fa647821eba0", "Daniel Roe"),
        FamiliaThemeId.Lavender to ThemePhoto("2aGaN2oDPQI", "photo-1593715857983-5531aa640471", "Thomas Despeyroux"),
        FamiliaThemeId.Coast to ThemePhoto("yfQRbv7oYCg", "photo-1500828131278-8de6878641b8", "José Duarte"),
    )

    fun url(theme: FamiliaThemeId): String? = photos[theme]?.url

    fun credit(theme: FamiliaThemeId): ThemePhoto? = photos[theme]

    /** Every photo, for [prefetchThemePhotos]. */
    val urls: List<String> get() = photos.values.map { it.url }
}

/**
 * Puts the 8 photos in the disk cache of Coil once (retour d'essai du 25/11: « charger les photos une fois pour
 * toutes »): a photo already there is not downloaded again, so a change of theme or a season shows its
 * photo at once, even offline. About 2 Mo in all.
 */
fun prefetchThemePhotos(context: android.content.Context) {
    val loader = coil.Coil.imageLoader(context)
    ThemePhotos.urls.forEach { url ->
        loader.enqueue(coil.request.ImageRequest.Builder(context).data(url).memoryCachePolicy(coil.request.CachePolicy.DISABLED).build())
    }
}

/** The photo of [theme], cropped to fill, over the gradient of its two colors until it is loaded. */
@Composable
fun ThemePhotoImage(theme: FamiliaThemeId, modifier: Modifier = Modifier) {
    val accent = Color(theme.accent ?: return)
    val dark = Color(theme.dark ?: return)
    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(dark, accent, dark))))
        ThemePhotos.url(theme)?.let { url ->
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** How far the Accueil has scrolled, for the photo behind it to glide (règle 9). */
@Stable
class PhotoScroll {
    var offset by mutableFloatStateOf(0f)
        private set

    fun reset() {
        offset = 0f
    }

    /** Follows any list under it: what it scrolled, down or up, never above its top. */
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            offset = (offset - consumed.y).coerceAtLeast(0f)
            return Offset.Zero
        }
    }
}

/** The photo is this much taller than the screen (A5): what it has left to glide. */
private const val PHOTO_HEIGHT = 1.18f

/** The photo moves at this part of the speed of the content. */
private const val PARALLAX = 0.25f

/**
 * The photo of [theme] behind the whole Accueil (règles 9 à 11): 118 % of the screen, gliding up
 * slowly as [scroll] goes down, under a light dark veil, darker at the top and the bottom, for the
 * white titles. Until the photo is loaded, or offline the first time, a gradient of the two colors of
 * the theme.
 */
@Composable
fun PhotoBackdrop(theme: FamiliaThemeId, scroll: PhotoScroll, modifier: Modifier = Modifier) {
    if (!theme.isPhoto) return
    val density = LocalDensity.current
    val top = LocalPhotoExtent.current == PhotoExtent.Top
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().then(if (top) Modifier.background(TopOnlyCream) else Modifier)) {
        // « Le haut seulement »: the photo covers the top 380 dp and melts into the cream.
        val shown: Dp = if (top) TOP_ONLY_HEIGHT else maxHeight
        val height: Dp = shown * PHOTO_HEIGHT
        val room = with(density) { (height - shown).toPx() }
        Box(
            Modifier
                .fillMaxWidth()
                .requiredHeight(height)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // requiredHeight centers the taller box: put its top at the top, then glide.
                    translationY = room / 2 - (scroll.offset * PARALLAX).coerceAtMost(room)
                },
        ) {
            ThemePhotoImage(theme, Modifier.fillMaxSize())
            if (top) Box(Modifier.fillMaxSize().background(TopOnlyVeil))
        }
        if (!top) Box(Modifier.fillMaxSize().background(PhotoVeil))
    }
}

/**
 * How much of the Accueil the photo covers (l'admin, 25/11 10 h 42), a choice of each phone: the whole
 * screen (v36, the default) or the top only, the tiles then white cards and the titles dark.
 */
enum class PhotoExtent(val key: String) {
    Full("full"),
    Top("top"),
    ;

    companion object {
        fun fromKey(key: String?): PhotoExtent = entries.firstOrNull { it.key == key } ?: Full
    }
}

/** The extent chosen on this phone; Réglages > Thème sets it. */
val LocalPhotoExtent = staticCompositionLocalOf { PhotoExtent.Full }

/** « Le haut seulement »: the height of the photo, and the cream it melts into. */
private val TOP_ONLY_HEIGHT = 380.dp
private val TopOnlyCream = Color(0xFFFAFAF7)

/** A lighter veil at the top for the white header, then the photo melts into the cream from 52 %. */
private val TopOnlyVeil = Brush.verticalGradient(
    0f to Color(0x59140C05),
    0.3f to Color(0x1A140C05),
    0.52f to Color(0x00FAFAF7),
    1f to TopOnlyCream,
)

/** The titles of section: white with a shadow on the photo, dark brown on the cream of « Le haut seulement ». */
val PhotoSectionText: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalPhotoExtent.current == PhotoExtent.Top) Color(0xFF3A2A1A) else Color.White

val PhotoSectionShadow: Shadow?
    @Composable @ReadOnlyComposable
    get() = if (LocalPhotoExtent.current == PhotoExtent.Top) null else PhotoTitleShadow

/** The veil of A5: rgba(20,12,5) at .45, .2 at 30 %, .24 at 60 %, .45 at the bottom. */
private val PhotoVeil = Brush.verticalGradient(
    0f to Color(0x73140C05),
    0.3f to Color(0x33140C05),
    0.6f to Color(0x3D140C05),
    1f to Color(0x73140C05),
)

/**
 * A card on the photo (règle 10, A5): frosted cream with a light white edge. Without the blur of
 * Android 12 for now, so at 85 % (the fallback of A5), which keeps every text readable whatever the
 * photo.
 */
@Composable
fun Modifier.photoCard(radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    // « Le haut seulement »: white cards at 92 % with a cream edge, never a blur.
    return if (LocalPhotoExtent.current == PhotoExtent.Top) {
        clip(shape).background(Color.White.copy(alpha = 0.92f)).border(1.dp, Color(0xFFF1EADF), shape)
    } else {
        clip(shape).background(PhotoCardColor).border(1.dp, Color.White.copy(alpha = 0.5f), shape)
    }
}

/** The cream of the cards on the photo, 85 % opaque. */
val PhotoCardColor = Color(0xD9FFFAF2)

/** The white titles on the photo (règle 11), with their shadow. */
val PhotoTitleShadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 2f), blurRadius = 6f)
