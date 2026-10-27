package com.crossfox.drive

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import java.util.Locale

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

fun fmt(n: Int): String = String.format(Locale("es", "PE"), "%,d", n)

fun installedPkg(ctx: Context, pkgs: List<String>): String? =
    pkgs.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }

fun launchPackage(ctx: Context, pkgs: List<String>, adjacent: Boolean = false): Boolean {
    val pkg = installedPkg(ctx, pkgs) ?: return false
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
    return try { ctx.startActivity(intent); true } catch (_: Exception) { false }
}

fun openApp(ctx: Context, label: String, pkgs: List<String>) {
    if (!launchPackage(ctx, pkgs)) toast(ctx, "«$label» no está instalada o no se pudo abrir")
}

fun labelFor(ctx: Context, pkg: String): String =
    Catalog.byPkg(pkg)?.label ?: try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

fun navigateTo(ctx: Context, query: String, useWaze: Boolean): Boolean {
    val enc = Uri.encode(query)
    val intent = if (useWaze)
        Intent(Intent.ACTION_VIEW, Uri.parse("waze://?q=$enc&navigate=yes")).setPackage("com.waze")
    else
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$enc")).setPackage("com.google.android.apps.maps")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { ctx.startActivity(intent); true } catch (_: Exception) { false }
}

/**
 * Intento OFICIAL de ventanas adyacentes: FLAG_ACTIVITY_LAUNCH_ADJACENT.
 * Android solo lo respeta si el sistema está en multiventana y la app destino admite redimensionar;
 * si no, la segunda app se abre a pantalla completa. No hay forma oficial de forzar tres ventanas.
 */
fun launchTaxiPair(ctx: Context): Boolean {
    val a = launchPackage(ctx, Catalog.WAZE.pkgs)
    val b = launchPackage(ctx, Catalog.INDRIVE.pkgs, adjacent = true)
    return a && b
}

fun netLabel(ctx: Context): String {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return "Sin datos"
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin red"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Conectado"
    }
}
