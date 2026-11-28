package com.gugaart.ui.screens.login

/** iPhone 发 UDP 广播需要组播权限（付费开发者账号），不做、也不显示这一组。 */
actual fun platformLanDiscovery(): LanDiscovery? = null
