package com.example.electronicshoppingapp


import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay

// ─── DATA MODELS ────────────────────────────────────────────────────────────

data class Product(
    val id: Int,
    val name: String,
    val brand: String,
    val price: Double,
    val originalPrice: Double,
    val rating: Float,
    val reviewCount: Int,
    val category: String,
    val imageUrl: String,
    val badge: String? = null,
    val specs: List<String> = emptyList(),
    val description: String = ""
)

data class CartItem(
    val product: Product,
    var quantity: Int
)

data class Order(
    val id: String,
    val items: List<CartItem>,
    val total: Double,
    val date: String,
    val status: String,
    val address: String
)

data class UserProfile(
    val name: String,
    val email: String,
    val phone: String,
    val address: String
)

// ─── APP STATE ───────────────────────────────────────────────────────────────

object AppState {
    var isLoggedIn by mutableStateOf(false)
    var currentUser by mutableStateOf<UserProfile?>(null)
    var cartItems = mutableStateListOf<CartItem>()
    var orders = mutableStateListOf<Order>()
    var wishlist = mutableStateListOf<Int>()

    fun addToCart(product: Product) {
        val existing = cartItems.find { it.product.id == product.id }
        if (existing != null) {
            val idx = cartItems.indexOf(existing)
            cartItems[idx] = existing.copy(quantity = existing.quantity + 1)
        } else {
            cartItems.add(CartItem(product, 1))
        }
    }

    fun removeFromCart(productId: Int) {
        cartItems.removeAll { it.product.id == productId }
    }

    fun updateQuantity(productId: Int, quantity: Int) {
        if (quantity <= 0) { removeFromCart(productId); return }
        val idx = cartItems.indexOfFirst { it.product.id == productId }
        if (idx != -1) cartItems[idx] = cartItems[idx].copy(quantity = quantity)
    }

    fun cartTotal(): Double = cartItems.sumOf { it.product.price * it.quantity }
    fun cartCount(): Int = cartItems.sumOf { it.quantity }

    fun toggleWishlist(id: Int) {
        if (wishlist.contains(id)) wishlist.remove(id) else wishlist.add(id)
    }

    fun placeOrder(address: String): Order {
        val order = Order(
            id = "#ORD${(10000..99999).random()}",
            items = cartItems.toList(),
            total = cartTotal(),
            date = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                .format(java.util.Date()),
            status = "Processing",
            address = address
        )
        orders.add(0, order)
        cartItems.clear()
        return order
    }
}

// ─── PERSISTENCE ─────────────────────────────────────────────────────────────

object Prefs {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("electrohub_prefs", Context.MODE_PRIVATE)
    }

    fun saveLogin(user: UserProfile) {
        prefs.edit()
            .putBoolean("logged_in", true)
            .putString("user", gson.toJson(user))
            .apply()
    }

    fun loadLogin(): UserProfile? {
        if (!prefs.getBoolean("logged_in", false)) return null
        val json = prefs.getString("user", null) ?: return null
        return gson.fromJson(json, UserProfile::class.java)
    }

    fun logout() = prefs.edit().putBoolean("logged_in", false).apply()

    fun saveOrders(orders: List<Order>) {
        val type = object : TypeToken<List<Order>>() {}.type
        prefs.edit().putString("orders", gson.toJson(orders, type)).apply()
    }

    fun loadOrders(): List<Order> {
        val json = prefs.getString("orders", null) ?: return emptyList()
        val type = object : TypeToken<List<Order>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }
}

// ─── PRODUCTS DATA ───────────────────────────────────────────────────────────

val sampleProducts = listOf(
    Product(1, "iPhone 15 Pro Max", "Apple", 134900.0, 154900.0, 4.8f, 2341,
        "Smartphones", "https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/iphone-15-pro-finish-select-202309-6-7inch-naturaltitanium?wid=800&hei=800&fmt=p-jpg",
        "HOT", listOf("A17 Pro Chip", "48MP Camera", "Titanium Build", "USB-C"),
        "The most advanced iPhone ever with titanium design and A17 Pro chip."),
    Product(2, "Samsung Galaxy S24 Ultra", "Samsung", 129999.0, 149999.0, 4.7f, 1876,
        "Smartphones", "https://images.samsung.com/is/image/samsung/p6pim/in/2401/gallery/in-galaxy-s24-ultra-s928-sm-s928bztgins-thumb-539573388?w=800&h=800",
        "NEW", listOf("Snapdragon 8 Gen 3", "200MP Camera", "S-Pen", "12GB RAM"),
        "Ultimate Galaxy experience with integrated S Pen and 200MP camera."),
    Product(3, "MacBook Pro 16\"", "Apple", 249900.0, 279900.0, 4.9f, 987,
        "Laptops", "https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/mbp16-spaceblack-select-202310?wid=800&hei=800&fmt=jpeg",
        "BESTSELLER", listOf("M3 Max Chip", "16\" Liquid Retina XDR", "36GB RAM", "18hr Battery"),
        "Supercharged by M3 Max for the most demanding workflows."),
    Product(4, "Sony WH-1000XM5", "Sony", 29990.0, 34990.0, 4.8f, 3421,
        "Audio", "https://www.bhphotovideo.com/images/images2500x2500/sony_wh1000xm5_b_wh_1000xm5_wireless_noise_canceling_headphones_1663088.jpg",
        "TOP RATED", listOf("30hr Battery", "ANC", "Hi-Res Audio", "Multipoint"),
        "Industry-leading noise cancellation with exceptional sound quality."),
    Product(5, "iPad Pro 12.9\"", "Apple", 112900.0, 124900.0, 4.7f, 1234,
        "Tablets", "https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/ipad-pro-finish-select-202212-12-9inch-space-gray?wid=800&hei=800",
        null, listOf("M2 Chip", "Liquid Retina XDR", "Apple Pencil 2", "Wi-Fi 6E"),
        "The ultimate iPad experience with M2 chip."),
    Product(6, "Dell XPS 15", "Dell", 159990.0, 179990.0, 4.6f, 876,
        "Laptops", "https://i.dell.com/is/image/DellContent/content/dam/ss2/product-images/dell-client-products/notebooks/xps-notebooks/xps-15-9530/media-gallery/black/notebook-xps-15-9530-black-gallery-1.psd?fmt=pjpg&pscan=auto&scl=1&wid=800&hei=800",
        null, listOf("13th Gen Intel i9", "32GB DDR5", "RTX 4070", "OLED Touch"),
        "Premium laptop with stunning OLED display and powerful performance."),
    Product(7, "Samsung 65\" Neo QLED", "Samsung", 184990.0, 219990.0, 4.6f, 543,
        "TVs", "https://images.samsung.com/is/image/samsung/p6pim/in/qa65qn95caklxl/gallery/in-neo-qled-qn95c-qa65qn95caklxl-537228887?w=800&h=800",
        "SALE", listOf("4K 120Hz", "Neo QLED", "Dolby Atmos", "Smart TV"),
        "Breathtaking 4K picture with Mini LED technology."),
    Product(8, "Apple Watch Ultra 2", "Apple", 89900.0, 99900.0, 4.8f, 765,
        "Wearables", "https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/MQDY3ref_VW_34FR+watch-49-titanium-ultra2_VW_34FR+watch-face-49-alpine-ultra2_VW_34FR?wid=800&hei=800",
        "NEW", listOf("Always-On Retina", "60hr Battery", "GPS+Cellular", "Titanium"),
        "The most capable and rugged Apple Watch ever."),
    Product(9, "Bose QuietComfort 45", "Bose", 26990.0, 31990.0, 4.7f, 2109,
        "Audio", "https://assets.bose.com/content/dam/Bose_DAM/Web/consumer_electronics/global/products/headphones/qc45/product_silo_images/qc45_black_hero.png/jcr:content/renditions/cq5dam.web.1280.1280.png",
        null, listOf("24hr Battery", "ANC", "Aware Mode", "USB-C"),
        "Legendary Bose noise cancellation with premium comfort."),
    Product(10, "OnePlus 12", "OnePlus", 64999.0, 74999.0, 4.6f, 1543,
        "Smartphones", "https://image01.oneplus.net/ebp/202401/09/1-m00-50-2a-rb8bwWWalx6acumxaadsq0wnq680879.png",
        "HOT", listOf("Snapdragon 8 Gen 3", "50MP Hasselblad", "100W Charging", "5500mAh"),
        "Flagship performance with Hasselblad camera tuning."),
    Product(11, "LG C3 OLED 55\"", "LG", 124990.0, 159990.0, 4.8f, 876,
        "TVs", "https://www.lg.com/in/images/tvs/md07553649/gallery/medium01.jpg",
        "BESTSELLER", listOf("OLED evo", "4K 120Hz", "Dolby Vision IQ", "G-Sync"),
        "Perfect blacks and infinite contrast with OLED technology."),
    Product(12, "Canon EOS R6 Mark II", "Canon", 214995.0, 239995.0, 4.8f, 432,
        "Cameras", "https://www.bhphotovideo.com/images/images2500x2500/canon_5666c002_eos_r6_mark_ii_mirrorless_1710921.jpg",
        null, listOf("40fps Burst", "4K 60fps", "IBIS", "Eye Tracking AF"),
        "Professional mirrorless camera with exceptional speed and accuracy."),
    Product(13, "Logitech MX Master 3S", "Logitech", 9995.0, 12995.0, 4.7f, 3201,
        "Accessories", "https://resource.logitech.com/w_800,c_lpad,ar_4:3,q_auto,f_auto,dpr_1.0/d_transparent.gif/content/dam/logitech/en/products/mice/mx-master-3s/gallery/mx-master-3s-mouse-top-view-graphite.png",
        null, listOf("8K DPI", "Quiet Click", "USB-C", "Multi-device"),
        "The master of all mice for advanced users."),
    Product(14, "Samsung Galaxy Tab S9 Ultra", "Samsung", 108999.0, 124999.0, 4.6f, 654,
        "Tablets", "https://images.samsung.com/is/image/samsung/p6pim/in/sm-x910nzaainu/gallery/in-galaxy-tab-s9-ultra-x910-sm-x910nzaainu-thumb-537727698?w=800&h=800",
        "NEW", listOf("Snapdragon 8 Gen 2", "14.6\" AMOLED", "S Pen", "12GB RAM"),
        "The biggest, most powerful Galaxy Tab yet."),
    Product(15, "JBL Charge 5", "JBL", 14999.0, 17999.0, 4.6f, 4321,
        "Audio", "https://in.jbl.com/dw/image/v2/BFND_PRD/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw0cec4527/JBL_Charge5_Hero_Sand_53429.png?sw=800&sh=800",
        null, listOf("20hr Battery", "IP67", "PartyBoost", "USB-C"),
        "Waterproof portable speaker with powerful sound."),
    Product(16, "Xiaomi Redmi Note 13 Pro+", "Xiaomi", 29999.0, 34999.0, 4.4f, 2876,
        "Smartphones", "https://i01.appmifile.com/v1/MI_18455B3E4DA706226CF7535A58E875F9/pms_1695889584.png",
        "SALE", listOf("Dimensity 7200 Ultra", "200MP OIS", "120W HyperCharge", "120Hz AMOLED"),
        "200MP photography meets 120W ultra-fast charging.")
)

val categories = listOf("All", "Smartphones", "Laptops", "Tablets", "Audio", "TVs", "Wearables", "Cameras", "Accessories")

// ─── MAIN ACTIVITY ───────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)

        // Restore login
        val savedUser = Prefs.loadLogin()
        if (savedUser != null) {
            AppState.isLoggedIn = true
            AppState.currentUser = savedUser
        }
        // Restore orders
        val savedOrders = Prefs.loadOrders()
        AppState.orders.addAll(savedOrders)

        setContent {
            ElectroHubTheme {
                ElectroHubApp()
            }
        }
    }
}

// ─── THEME ───────────────────────────────────────────────────────────────────

val ElectroBlue = Color(0xFF0057FF)
val ElectroDark = Color(0xFF0A0E1A)
val ElectroCard = Color(0xFF111827)
val ElectroSurface = Color(0xFF1A2235)
val ElectroAccent = Color(0xFF00D4FF)
val ElectroGold = Color(0xFFFFC107)
val ElectroGreen = Color(0xFF00C853)
val ElectroRed = Color(0xFFFF3B30)
val ElectroText = Color(0xFFF0F4FF)
val ElectroSubtext = Color(0xFF8899BB)

@Composable
fun ElectroHubTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = ElectroBlue,
        secondary = ElectroAccent,
        background = ElectroDark,
        surface = ElectroCard,
        onPrimary = Color.White,
        onBackground = ElectroText,
        onSurface = ElectroText,
        tertiary = ElectroGold
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ─── NAVIGATION ──────────────────────────────────────────────────────────────

enum class Screen { Login, Home, ProductDetail, Cart, Profile, Orders, Search, Checkout }

@Composable
fun ElectroHubApp() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(if (AppState.isLoggedIn) Screen.Home else Screen.Login) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var selectedNav by remember { mutableStateOf(0) }

    // Save orders on change
    LaunchedEffect(AppState.orders.size) {
        Prefs.saveOrders(AppState.orders.toList())
    }

    when (screen) {
        Screen.Login -> LoginScreen(
            onLoginSuccess = { user ->
                AppState.isLoggedIn = true
                AppState.currentUser = user
                Prefs.saveLogin(user)
                screen = Screen.Home
            }
        )
        Screen.Home -> MainScaffold(
            selectedNav = selectedNav,
            onNavChange = { idx ->
                selectedNav = idx
                screen = when (idx) {
                    0 -> Screen.Home
                    1 -> Screen.Search
                    2 -> Screen.Cart
                    3 -> Screen.Orders
                    4 -> Screen.Profile
                    else -> Screen.Home
                }
            },
            cartCount = AppState.cartCount()
        ) {
            HomeScreen(onProductClick = { product ->
                selectedProduct = product
                screen = Screen.ProductDetail
            })
        }
        Screen.Search -> MainScaffold(selectedNav = 1,
            onNavChange = { idx ->
                selectedNav = idx
                screen = when (idx) { 0 -> Screen.Home; 2 -> Screen.Cart; 3 -> Screen.Orders; 4 -> Screen.Profile; else -> Screen.Search }
            }, cartCount = AppState.cartCount()
        ) { SearchScreen(onProductClick = { selectedProduct = it; screen = Screen.ProductDetail }) }

        Screen.Cart -> MainScaffold(selectedNav = 2,
            onNavChange = { idx ->
                selectedNav = idx
                screen = when (idx) { 0 -> Screen.Home; 1 -> Screen.Search; 3 -> Screen.Orders; 4 -> Screen.Profile; else -> Screen.Cart }
            }, cartCount = AppState.cartCount()
        ) { CartScreen(onCheckout = { screen = Screen.Checkout }) }

        Screen.Orders -> MainScaffold(selectedNav = 3,
            onNavChange = { idx ->
                selectedNav = idx
                screen = when (idx) { 0 -> Screen.Home; 1 -> Screen.Search; 2 -> Screen.Cart; 4 -> Screen.Profile; else -> Screen.Orders }
            }, cartCount = AppState.cartCount()
        ) { OrdersScreen() }

        Screen.Profile -> MainScaffold(selectedNav = 4,
            onNavChange = { idx ->
                selectedNav = idx
                screen = when (idx) { 0 -> Screen.Home; 1 -> Screen.Search; 2 -> Screen.Cart; 3 -> Screen.Orders; else -> Screen.Profile }
            }, cartCount = AppState.cartCount()
        ) {
            ProfileScreen(onLogout = {
                AppState.isLoggedIn = false
                AppState.currentUser = null
                AppState.cartItems.clear()
                Prefs.logout()
                screen = Screen.Login
                selectedNav = 0
            })
        }

        Screen.ProductDetail -> selectedProduct?.let { product ->
            ProductDetailScreen(
                product = product,
                onBack = { screen = Screen.Home },
                onGoToCart = { screen = Screen.Cart; selectedNav = 2 }
            )
        }

        Screen.Checkout -> CheckoutScreen(
            onBack = { screen = Screen.Cart },
            onOrderPlaced = { screen = Screen.Orders; selectedNav = 3 }
        )
    }
}

// ─── MAIN SCAFFOLD ────────────────────────────────────────────────────────────

@Composable
fun MainScaffold(selectedNav: Int, onNavChange: (Int) -> Unit, cartCount: Int, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(ElectroDark)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            BottomNavBar(selectedNav, onNavChange, cartCount)
        }
    }
}

@Composable
fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit, cartCount: Int) {
    Surface(color = ElectroCard, shadowElevation = 16.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple("Home", Icons.Filled.Home, Icons.Outlined.Home),
                Triple("Search", Icons.Filled.Search, Icons.Outlined.Search),
                Triple("Cart", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
                Triple("Orders", Icons.Filled.Receipt, Icons.Outlined.ReceiptLong),
                Triple("Profile", Icons.Filled.Person, Icons.Outlined.Person)
            )
            items.forEachIndexed { idx, (label, filled, outline) ->
                Box(contentAlignment = Alignment.TopEnd) {
                    Column(
                        Modifier.clickable { onSelect(idx) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isSelected = selected == idx
                        Icon(
                            if (isSelected) filled else outline,
                            label,
                            tint = if (isSelected) ElectroBlue else ElectroSubtext,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            color = if (isSelected) ElectroBlue else ElectroSubtext,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    if (idx == 2 && cartCount > 0) {
                        Badge(containerColor = ElectroRed, modifier = Modifier.offset(x = (-4).dp)) {
                            Text(if (cartCount > 9) "9+" else "$cartCount", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── LOGIN SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(onLoginSuccess: (UserProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val bgOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgOffset"
    )

    Box(Modifier.fillMaxSize().background(ElectroDark)) {
        // Animated gradient background
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(ElectroBlue.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(size.width * bgOffset, size.height * 0.3f),
                    radius = size.width * 0.8f
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(ElectroAccent.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width * (1 - bgOffset), size.height * 0.7f),
                    radius = size.width * 0.6f
                )
            )
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(24.dp).statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // Logo
            Box(
                Modifier.size(90.dp)
                    .background(Brush.linearGradient(listOf(ElectroBlue, ElectroAccent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ElectricBolt, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("ElectroHub", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = ElectroText, letterSpacing = (-1).sp)
            Text("Your Premium Tech Store", fontSize = 14.sp, color = ElectroSubtext)
            Spacer(Modifier.height(40.dp))

            // Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ElectroCard.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(28.dp)) {
                    Text(
                        if (isRegister) "Create Account" else "Welcome Back",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ElectroText
                    )
                    Text(
                        if (isRegister) "Sign up to get started" else "Sign in to continue",
                        fontSize = 13.sp, color = ElectroSubtext
                    )
                    Spacer(Modifier.height(24.dp))

                    if (isRegister) {
                        ElectroTextField(name, { name = it }, "Full Name", Icons.Outlined.Person)
                        Spacer(Modifier.height(12.dp))
                    }
                    ElectroTextField(email, { email = it }, "Email Address", Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email)
                    Spacer(Modifier.height(12.dp))
                    ElectroTextField(
                        password, { password = it }, "Password", Icons.Outlined.Lock,
                        isPassword = true, passwordVisible = passwordVisible,
                        onPasswordToggle = { passwordVisible = !passwordVisible }
                    )

                    if (errorMsg.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg, color = ElectroRed, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            errorMsg = ""
                            if (email.isBlank() || password.isBlank()) {
                                errorMsg = "Please fill all fields"; return@Button
                            }
                            if (isRegister && name.isBlank()) {
                                errorMsg = "Please enter your name"; return@Button
                            }
                            isLoading = true
                            val user = UserProfile(
                                name = if (isRegister) name else email.substringBefore("@").replaceFirstChar { it.uppercase() },
                                email = email,
                                phone = "+91 9876543210",
                                address = "Mumbai, Maharashtra, India"
                            )
                            onLoginSuccess(user)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectroBlue
                        )
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text(if (isRegister) "Create Account" else "Sign In",
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            if (isRegister) "Already have an account? " else "Don't have an account? ",
                            color = ElectroSubtext, fontSize = 13.sp
                        )
                        Text(
                            if (isRegister) "Sign In" else "Sign Up",
                            color = ElectroBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { isRegister = !isRegister; errorMsg = "" }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            // Quick demo login
            OutlinedButton(
                onClick = {
                    onLoginSuccess(UserProfile("Demo User", "demo@electrohub.com", "+91 9876543210", "Mumbai, Maharashtra"))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ElectroSubtext.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Filled.FlashOn, null, tint = ElectroAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quick Demo Login", color = ElectroText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ElectroTextField(
    value: String, onValueChange: (String) -> Unit, label: String, leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false, passwordVisible: Boolean = false, onPasswordToggle: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { Icon(leadingIcon, null, tint = ElectroSubtext, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (isPassword) ({
            IconButton(onClick = { onPasswordToggle?.invoke() }) {
                Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    null, tint = ElectroSubtext)
            }
        }) else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectroBlue, unfocusedBorderColor = ElectroSurface,
            focusedTextColor = ElectroText, unfocusedTextColor = ElectroText,
            cursorColor = ElectroBlue, focusedLabelColor = ElectroBlue,
            unfocusedLabelColor = ElectroSubtext, focusedContainerColor = ElectroSurface,
            unfocusedContainerColor = ElectroSurface
        ),
        singleLine = true
    )
}

// ─── HOME SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(onProductClick: (Product) -> Unit) {
    var selectedCategory by remember { mutableStateOf("All") }
    val filteredProducts = remember(selectedCategory) {
        if (selectedCategory == "All") sampleProducts
        else sampleProducts.filter { it.category == selectedCategory }
    }
    var searchQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(ElectroDark)) {
        // Header
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(ElectroCard, ElectroDark))
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hello, ${AppState.currentUser?.name?.split(" ")?.first() ?: "there"} 👋",
                            color = ElectroSubtext, fontSize = 14.sp)
                        Text("Find your perfect gadget", color = ElectroText,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(44.dp).background(ElectroBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(AppState.currentUser?.name?.first()?.toString() ?: "U",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Search bar
                Surface(shape = RoundedCornerShape(14.dp), color = ElectroSurface) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = ElectroSubtext, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Search phones, laptops, audio...", color = ElectroSubtext, fontSize = 14.sp)
                    }
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Banner
            item {
                PromoBanner()
            }

            // Categories
            item {
                Text("Categories", color = ElectroText, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(categories) { cat ->
                        CategoryChip(cat, cat == selectedCategory) { selectedCategory = cat }
                    }
                }
            }

            // Flash deals
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, null, tint = ElectroGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Flash Deals", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Text("See all", color = ElectroBlue, fontSize = 13.sp)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(sampleProducts.filter { it.badge == "SALE" || it.badge == "HOT" }) { product ->
                        FlashDealCard(product, onProductClick)
                    }
                }
            }

            // Products grid header
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedCategory == "All") "All Products" else selectedCategory,
                        color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                    Text("${filteredProducts.size} items", color = ElectroSubtext, fontSize = 13.sp)
                }
            }

            // Products 2-column grid via pairs
            val pairs = filteredProducts.chunked(2)
            items(pairs) { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { product ->
                        Box(Modifier.weight(1f)) {
                            ProductCard(product, onProductClick)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun PromoBanner() {
    val banners = listOf(
        Triple("🔥 Mega Sale!", "Up to 30% off on all gadgets", ElectroBlue),
        Triple("📱 New Arrivals", "Latest smartphones in stock", Color(0xFF6C3FE6)),
        Triple("🎧 Audio Week", "Best deals on headphones", Color(0xFF00897B))
    )
    var currentBanner by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(3000); currentBanner = (currentBanner + 1) % banners.size }
    }

    val (title, subtitle, color) = banners[currentBanner]
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))
            .padding(20.dp)
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                Text("Shop Now →", color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Icon(Icons.Filled.ElectricBolt, null, tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(100.dp).align(Alignment.CenterEnd).offset(x = 10.dp))
        // Dots
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            banners.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == currentBanner) 18.dp else 6.dp, 6.dp)
                        .background(if (i == currentBanner) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) ElectroBlue else ElectroSurface,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            name,
            color = if (selected) Color.White else ElectroSubtext,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun FlashDealCard(product: Product, onClick: (Product) -> Unit) {
    val discount = ((1 - product.price / product.originalPrice) * 100).toInt()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ElectroCard,
        modifier = Modifier.width(160.dp).clickable { onClick(product) }
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(130.dp)
                    .background(ElectroSurface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(product.imageUrl)
                        .crossfade(true).build(),
                    contentDescription = product.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp), color = ElectroRed,
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                ) {
                    Text("-$discount%", color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(product.name, color = ElectroText, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("₹${formatPrice(product.price)}", color = ElectroBlue,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("₹${formatPrice(product.originalPrice)}", color = ElectroSubtext,
                    fontSize = 11.sp, textDecoration = TextDecoration.LineThrough)
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, onClick: (Product) -> Unit) {
    val isWishlisted = AppState.wishlist.contains(product.id)
    val discount = ((1 - product.price / product.originalPrice) * 100).toInt()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ElectroCard,
        modifier = Modifier.fillMaxWidth().clickable { onClick(product) }
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .background(ElectroSurface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(product.imageUrl)
                        .crossfade(true).build(),
                    contentDescription = product.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
                product.badge?.let {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (it) {
                            "HOT" -> ElectroRed; "NEW" -> ElectroGreen
                            "SALE" -> ElectroGold; else -> ElectroBlue
                        },
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                    ) {
                        Text(it, color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { AppState.toggleWishlist(product.id) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        null,
                        tint = if (isWishlisted) ElectroRed else ElectroSubtext,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(product.brand, color = ElectroBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(product.name, color = ElectroText, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = ElectroGold, modifier = Modifier.size(13.dp))
                    Text(" ${product.rating}", color = ElectroGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(" (${product.reviewCount})", color = ElectroSubtext, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("₹${formatPrice(product.price)}", color = ElectroText,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("-$discount%", color = ElectroGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("₹${formatPrice(product.originalPrice)}", color = ElectroSubtext,
                    fontSize = 11.sp, textDecoration = TextDecoration.LineThrough)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { AppState.addToCart(product) },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectroBlue),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.AddShoppingCart, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add to Cart", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── PRODUCT DETAIL SCREEN ───────────────────────────────────────────────────

@Composable
fun ProductDetailScreen(product: Product, onBack: () -> Unit, onGoToCart: () -> Unit) {
    var addedToCart by remember { mutableStateOf(false) }
    val discount = ((1 - product.price / product.originalPrice) * 100).toInt()
    val isWishlisted = AppState.wishlist.contains(product.id)

    Column(Modifier.fillMaxSize().background(ElectroDark)) {
        // Top bar
        Box(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.ArrowBack, null, tint = ElectroText)
            }
            Text("Product Details", color = ElectroText, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = { AppState.toggleWishlist(product.id) },
                modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null, tint = if (isWishlisted) ElectroRed else ElectroText
                )
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                // Product image
                Box(
                    Modifier.fillMaxWidth().height(280.dp)
                        .background(ElectroSurface),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(product.imageUrl)
                            .crossfade(true).build(),
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(260.dp).padding(24.dp)
                    )
                    product.badge?.let {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (it) {
                                "HOT" -> ElectroRed; "NEW" -> ElectroGreen
                                "SALE" -> ElectroGold; else -> ElectroBlue
                            },
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        ) {
                            Text(it, color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(Modifier.padding(20.dp)) {
                    Text(product.brand, color = ElectroBlue, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(product.name, color = ElectroText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(5) { i ->
                                Icon(Icons.Filled.Star, null,
                                    tint = if (i < product.rating.toInt()) ElectroGold else ElectroSubtext,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${product.rating}", color = ElectroGold, fontWeight = FontWeight.Bold)
                        Text(" (${product.reviewCount} reviews)", color = ElectroSubtext, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("₹${formatPrice(product.price)}", color = ElectroText,
                            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("₹${formatPrice(product.originalPrice)}", color = ElectroSubtext,
                                textDecoration = TextDecoration.LineThrough)
                            Surface(shape = RoundedCornerShape(6.dp), color = ElectroGreen.copy(alpha = 0.15f)) {
                                Text("$discount% OFF", color = ElectroGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Inclusive of all taxes", color = ElectroSubtext, fontSize = 11.sp)

                    Spacer(Modifier.height(20.dp))
                    // Delivery info
                    Surface(shape = RoundedCornerShape(12.dp), color = ElectroSurface) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalShipping, null, tint = ElectroGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Free Delivery", color = ElectroText, fontWeight = FontWeight.SemiBold)
                                Text("Delivery by ${getDeliveryDate()}", color = ElectroSubtext, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("Specifications", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    product.specs.forEachIndexed { i, spec ->
                        val parts = spec.split(":")
                        Row(
                            Modifier.fillMaxWidth().background(
                                if (i % 2 == 0) ElectroSurface else ElectroCard,
                                RoundedCornerShape(8.dp)
                            ).padding(12.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = ElectroBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(spec, color = ElectroText, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("About this product", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(product.description, color = ElectroSubtext, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }

        // Bottom bar
        Surface(color = ElectroCard, shadowElevation = 16.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { AppState.addToCart(product); addedToCart = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, ElectroBlue)
                ) {
                    Icon(Icons.Filled.AddShoppingCart, null, tint = ElectroBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to Cart", color = ElectroBlue, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { AppState.addToCart(product); onGoToCart() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectroBlue)
                ) {
                    Icon(Icons.Filled.ShoppingBag, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buy Now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── SEARCH SCREEN ────────────────────────────────────────────────────────────

@Composable
fun SearchScreen(onProductClick: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        if (query.length < 2) emptyList()
        else sampleProducts.filter {
            it.name.contains(query, true) || it.brand.contains(query, true) || it.category.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize().background(ElectroDark).statusBarsPadding()) {
        Text("Search", color = ElectroText, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search products...", color = ElectroSubtext) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = ElectroSubtext) },
            trailingIcon = if (query.isNotEmpty()) ({
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Filled.Clear, null, tint = ElectroSubtext)
                }
            }) else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectroBlue, unfocusedBorderColor = ElectroSurface,
                focusedTextColor = ElectroText, unfocusedTextColor = ElectroText,
                cursorColor = ElectroBlue, focusedContainerColor = ElectroSurface,
                unfocusedContainerColor = ElectroSurface
            ),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        if (query.length < 2) {
            // Trending
            Text("Trending", color = ElectroText, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("iPhone 15", "MacBook Pro", "Sony WH-1000XM5", "Samsung S24", "iPad Pro")) { tag ->
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = ElectroSurface,
                        modifier = Modifier.clickable { query = tag }
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, null, tint = ElectroBlue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tag, color = ElectroText, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.SearchOff, null, tint = ElectroSubtext, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No results for \"$query\"", color = ElectroSubtext)
                }
            }
        } else {
            Text("${results.size} results", color = ElectroSubtext,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results) { product ->
                    SearchResultItem(product, onProductClick)
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(product: Product, onClick: (Product) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = ElectroCard,
        modifier = Modifier.fillMaxWidth().clickable { onClick(product) }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(product.imageUrl).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(72.dp).background(ElectroSurface, RoundedCornerShape(10.dp)).padding(8.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.brand, color = ElectroBlue, fontSize = 11.sp)
                Text(product.name, color = ElectroText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("₹${formatPrice(product.price)}", color = ElectroText, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { AppState.addToCart(product) }) {
                Icon(Icons.Filled.AddShoppingCart, null, tint = ElectroBlue)
            }
        }
    }
}

// ─── CART SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun CartScreen(onCheckout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ElectroDark).statusBarsPadding()) {
        Text("My Cart", color = ElectroText, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

        if (AppState.cartItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ShoppingCart, null, tint = ElectroSubtext, modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Your cart is empty", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Add items to get started", color = ElectroSubtext)
                }
            }
            return
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AppState.cartItems, key = { it.product.id }) { item ->
                CartItemCard(item)
            }
            item {
                // Order summary
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = ElectroCard) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Order Summary", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        SummaryRow("Subtotal", "₹${formatPrice(AppState.cartTotal())}")
                        SummaryRow("Delivery", "FREE")
                        SummaryRow("Discount", "-₹${formatPrice(AppState.cartItems.sumOf { (it.product.originalPrice - it.product.price) * it.quantity })}")
                        Divider(color = ElectroSurface, modifier = Modifier.padding(vertical = 10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("₹${formatPrice(AppState.cartTotal())}", color = ElectroText,
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Inclusive of all taxes", color = ElectroSubtext, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Checkout button
        Surface(color = ElectroCard, shadowElevation = 16.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Total Amount", color = ElectroSubtext, fontSize = 12.sp)
                        Text("₹${formatPrice(AppState.cartTotal())}", color = ElectroText,
                            fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                    Button(
                        onClick = onCheckout,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectroBlue)
                    ) {
                        Text("Proceed to Checkout", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemCard(item: CartItem) {
    Surface(shape = RoundedCornerShape(16.dp), color = ElectroCard) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.product.imageUrl)
                    .crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(90.dp)
                    .background(ElectroSurface, RoundedCornerShape(12.dp)).padding(8.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.product.brand, color = ElectroBlue, fontSize = 11.sp)
                Text(item.product.name, color = ElectroText, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("₹${formatPrice(item.product.price)}", color = ElectroText,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quantity controls
                    Surface(shape = RoundedCornerShape(8.dp), color = ElectroSurface) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { AppState.updateQuantity(item.product.id, item.quantity - 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Remove, null, tint = ElectroText, modifier = Modifier.size(16.dp))
                            }
                            Text("${item.quantity}", color = ElectroText, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(
                                onClick = { AppState.updateQuantity(item.product.id, item.quantity + 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, tint = ElectroText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { AppState.removeFromCart(item.product.id) }) {
                        Icon(Icons.Outlined.Delete, null, tint = ElectroRed, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ElectroSubtext, fontSize = 14.sp)
        Text(value, color = if (label == "Delivery") ElectroGreen else ElectroText, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

// ─── CHECKOUT SCREEN ──────────────────────────────────────────────────────────

@Composable
fun CheckoutScreen(onBack: () -> Unit, onOrderPlaced: () -> Unit) {
    var address by remember { mutableStateOf(AppState.currentUser?.address ?: "") }
    var selectedPayment by remember { mutableStateOf("UPI") }
    var isPlacing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ElectroDark)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = ElectroText) }
            Text("Checkout", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SectionCard("Delivery Address") {
                    OutlinedTextField(
                        value = address, onValueChange = { address = it },
                        label = { Text("Full Address") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectroBlue, unfocusedBorderColor = ElectroSurface,
                            focusedTextColor = ElectroText, unfocusedTextColor = ElectroText,
                            cursorColor = ElectroBlue, focusedContainerColor = ElectroSurface,
                            unfocusedContainerColor = ElectroSurface, focusedLabelColor = ElectroBlue,
                            unfocusedLabelColor = ElectroSubtext
                        )
                    )
                }
            }
            item {
                SectionCard("Payment Method") {
                    listOf(
                        Triple("UPI", Icons.Filled.QrCode, "Pay via UPI apps"),
                        Triple("Card", Icons.Filled.CreditCard, "Debit / Credit Card"),
                        Triple("COD", Icons.Filled.Money, "Cash on Delivery")
                    ).forEach { (method, icon, desc) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(
                                    if (selectedPayment == method) ElectroBlue.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedPayment = method }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedPayment == method, onClick = { selectedPayment = method },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectroBlue))
                            Icon(icon, null, tint = if (selectedPayment == method) ElectroBlue else ElectroSubtext,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(method, color = ElectroText, fontWeight = FontWeight.SemiBold)
                                Text(desc, color = ElectroSubtext, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard("Order Items (${AppState.cartItems.size})") {
                    AppState.cartItems.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(item.product.imageUrl)
                                    .crossfade(true).build(),
                                contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.size(48.dp).background(ElectroSurface, RoundedCornerShape(8.dp)).padding(4.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item.product.name, color = ElectroText, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("x${item.quantity}", color = ElectroSubtext)
                            Spacer(Modifier.width(8.dp))
                            Text("₹${formatPrice(item.product.price * item.quantity)}", color = ElectroText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Surface(color = ElectroCard, shadowElevation = 16.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Payable", color = ElectroSubtext)
                    Text("₹${formatPrice(AppState.cartTotal())}", color = ElectroText, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        isPlacing = true
                        AppState.placeOrder(address)
                        onOrderPlaced()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectroGreen)
                ) {
                    if (isPlacing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else {
                        Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Place Order", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = ElectroCard) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ─── ORDERS SCREEN ────────────────────────────────────────────────────────────

@Composable
fun OrdersScreen() {
    Column(Modifier.fillMaxSize().background(ElectroDark).statusBarsPadding()) {
        Text("Recent Orders", color = ElectroText, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))

        if (AppState.orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ReceiptLong, null, tint = ElectroSubtext, modifier = Modifier.size(80.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No orders yet", color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Your orders will appear here", color = ElectroSubtext)
                }
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AppState.orders) { order ->
                OrderCard(order)
            }
        }
    }
}

@Composable
fun OrderCard(order: Order) {
    val statusColor = when (order.status) {
        "Delivered" -> ElectroGreen; "Cancelled" -> ElectroRed
        "Shipped" -> ElectroAccent; else -> ElectroGold
    }
    Surface(shape = RoundedCornerShape(16.dp), color = ElectroCard) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(order.id, color = ElectroText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(order.status, color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(order.date, color = ElectroSubtext, fontSize = 12.sp)

            Divider(color = ElectroSurface, modifier = Modifier.padding(vertical = 12.dp))

            // Items preview
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order.items.take(3)) { item ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(item.product.imageUrl)
                            .crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp)
                            .background(ElectroSurface, RoundedCornerShape(8.dp)).padding(6.dp)
                    )
                }
                if (order.items.size > 3) {
                    item {
                        Box(
                            Modifier.size(56.dp).background(ElectroSurface, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("+${order.items.size - 3}", color = ElectroSubtext, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("${order.items.sumOf { it.quantity }} items", color = ElectroSubtext, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total Paid", color = ElectroSubtext, fontSize = 12.sp)
                    Text("₹${formatPrice(order.total)}", color = ElectroText,
                        fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = ElectroSurface) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = ElectroBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(order.address.take(20) + if (order.address.length > 20) "..." else "",
                            color = ElectroSubtext, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ─── PROFILE SCREEN ───────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val user = AppState.currentUser

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = ElectroCard,
            title = { Text("Logout", color = ElectroText, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to logout?", color = ElectroSubtext) },
            confirmButton = {
                Button(onClick = { showLogoutDialog = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectroRed)) {
                    Text("Logout")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false },
                    border = BorderStroke(1.dp, ElectroSubtext)) {
                    Text("Cancel", color = ElectroText)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(ElectroDark).statusBarsPadding()) {
        // Header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(ElectroBlue, ElectroCard)))
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(80.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(user?.name?.first()?.toString() ?: "U",
                        color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(12.dp))
                Text(user?.name ?: "User", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(user?.email ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StatChip("${AppState.orders.size}", "Orders")
                    StatChip("${AppState.wishlist.size}", "Wishlist")
                    StatChip("${AppState.cartCount()}", "In Cart")
                }
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                // Account info
                SectionCard("Account Information") {
                    InfoRow(Icons.Filled.Person, "Name", user?.name ?: "")
                    InfoRow(Icons.Filled.Email, "Email", user?.email ?: "")
                    InfoRow(Icons.Filled.Phone, "Phone", user?.phone ?: "")
                    InfoRow(Icons.Filled.LocationOn, "Address", user?.address ?: "")
                }
            }
            item {
                // Settings
                Surface(shape = RoundedCornerShape(16.dp), color = ElectroCard) {
                    Column(Modifier.fillMaxWidth()) {
                        SettingsItem(Icons.Outlined.Notifications, "Notifications", "Manage alerts") {}
                        Divider(color = ElectroSurface)
                        SettingsItem(Icons.Outlined.Security, "Privacy & Security", "Manage security") {}
                        Divider(color = ElectroSurface)
                        SettingsItem(Icons.Outlined.HelpOutline, "Help & Support", "Get help") {}
                        Divider(color = ElectroSurface)
                        SettingsItem(Icons.Outlined.Info, "About ElectroHub", "Version 1.0.0") {}
                    }
                }
            }
            item {
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectroRed.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Filled.Logout, null, tint = ElectroRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout", color = ElectroRed, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun StatChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ElectroBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = ElectroSubtext, fontSize = 11.sp)
            Text(value, color = ElectroText, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = ElectroSurface) {
            Icon(icon, null, tint = ElectroBlue, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ElectroText, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = ElectroSubtext, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = ElectroSubtext)
    }
}

// ─── UTILITIES ───────────────────────────────────────────────────────────────

fun formatPrice(price: Double): String {
    return if (price >= 100000) {
        String.format("%.2f L", price / 100000)
    } else {
        String.format("%,.0f", price)
    }
}

fun getDeliveryDate(): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_MONTH, (3..5).random())
    return java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault()).format(cal.time)
}